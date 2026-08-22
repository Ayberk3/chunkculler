package com.sektor.verticalantiesp.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.sektor.verticalantiesp.VerticalAntiESP;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EntityTrackingManager {

    private final VerticalAntiESP plugin;
    // Map of Viewer UUID -> Set of Hidden Entity IDs
    private final Map<UUID, Set<Integer>> hiddenEntities = new ConcurrentHashMap<>();

    public EntityTrackingManager(VerticalAntiESP plugin) {
        this.plugin = plugin;
    }

    public boolean isEntityHidden(UUID viewerUUID, int entityId) {
        Set<Integer> set = hiddenEntities.get(viewerUUID);
        return set != null && set.contains(entityId);
    }

    public void hideEntity(Player viewer, Entity target) {
        if (viewer == null || !viewer.isOnline() || target == null || !target.isValid()) {
            return;
        }

        Set<Integer> set = hiddenEntities.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        int entityId = target.getEntityId();

        if (set.add(entityId)) {
            // Send Destroy Packet to viewer
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);

            // Also hide passengers if applicable
            for (Entity passenger : target.getPassengers()) {
                hideEntity(viewer, passenger);
            }

            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Hidden entity #" + entityId + " (" + target.getType() + ") from " + viewer.getName());
            }
        }
    }

    public void showEntity(Player viewer, Entity target) {
        if (viewer == null || !viewer.isOnline() || target == null || !target.isValid()) {
            return;
        }

        Set<Integer> set = hiddenEntities.get(viewer.getUniqueId());
        if (set == null || !set.remove(target.getEntityId())) {
            return; // Not hidden
        }

        int entityId = target.getEntityId();
        Location loc = target.getLocation();
        UUID uuid = target.getUniqueId();

        // 1. Convert Bukkit EntityType to PacketEvents EntityType
        EntityType packetEntityType = SpigotConversionUtil.fromBukkitEntityType(target.getType());
        if (packetEntityType == null) {
            packetEntityType = EntityTypes.PIG; // Fallback
        }

        // 2. Build SPAWN Packet
        Vector3d position = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                packetEntityType,
                position,
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(), // Head yaw
                0, // Data
                Optional.empty() // Velocity
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);

        // 3. Head Rotation Packet
        WrapperPlayServerEntityHeadLook headLook = new WrapperPlayServerEntityHeadLook(entityId, loc.getYaw());
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, headLook);

        // 4. Equipment Packet (if LivingEntity with armor/weapons)
        if (target instanceof LivingEntity living) {
            EntityEquipment eq = living.getEquipment();
            if (eq != null) {
                List<Equipment> equipmentList = new ArrayList<>();
                addEquipment(equipmentList, EquipmentSlot.MAIN_HAND, eq.getItemInMainHand());
                addEquipment(equipmentList, EquipmentSlot.OFF_HAND, eq.getItemInOffHand());
                addEquipment(equipmentList, EquipmentSlot.HELMET, eq.getHelmet());
                addEquipment(equipmentList, EquipmentSlot.CHEST_PLATE, eq.getChestplate());
                addEquipment(equipmentList, EquipmentSlot.LEGGINGS, eq.getLeggings());
                addEquipment(equipmentList, EquipmentSlot.BOOTS, eq.getBoots());

                if (!equipmentList.isEmpty()) {
                    WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(entityId, equipmentList);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, equipmentPacket);
                }
            }
        }

        // 5. Passengers Packet (if entity has passengers)
        List<Entity> passengers = target.getPassengers();
        if (!passengers.isEmpty()) {
            int[] passengerIds = new int[passengers.size()];
            for (int i = 0; i < passengers.size(); i++) {
                Entity p = passengers.get(i);
                passengerIds[i] = p.getEntityId();
                // Ensure passenger is also shown
                showEntity(viewer, p);
            }
            WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(entityId, passengerIds);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
        }

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Revealed entity #" + entityId + " (" + target.getType() + ") to " + viewer.getName());
        }
    }

    private void addEquipment(List<Equipment> list, EquipmentSlot slot, org.bukkit.inventory.ItemStack bukkitItem) {
        if (bukkitItem != null && bukkitItem.getType() != org.bukkit.Material.AIR) {
            ItemStack item = SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
            list.add(new Equipment(slot, item));
        }
    }

    public boolean isTargetApplicable(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return false;
        }

        // NEVER hide projectiles (Arrows, Ender Pearls, Tridents, etc.)
        if (entity instanceof Projectile || entity instanceof FallingBlock || entity instanceof Item) {
            return false;
        }

        if (entity instanceof Player) {
            return plugin.getConfigManager().isHidePlayers();
        }

        if (entity instanceof Monster) {
            return plugin.getConfigManager().isHideMonsters();
        }

        if (entity instanceof Animals || entity instanceof WaterMob) {
            return plugin.getConfigManager().isHideAnimals();
        }

        // Armor stands, Vehicle, Minecarts etc.
        return plugin.getConfigManager().isHideMisc();
    }

    public void removeViewer(UUID viewerUUID) {
        hiddenEntities.remove(viewerUUID);
    }

    public void removeEntity(int entityId) {
        for (Set<Integer> set : hiddenEntities.values()) {
            set.remove(entityId);
        }
    }

    public void clearAll() {
        hiddenEntities.clear();
    }
}
