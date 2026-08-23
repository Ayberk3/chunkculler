package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityTrackingManager {

    private final Main plugin;
    private final Map<UUID, Set<Integer>> hiddenEntities = new ConcurrentHashMap<>();

    public EntityTrackingManager(Main plugin) {
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
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);

            for (Entity passenger : target.getPassengers()) {
                hideEntity(viewer, passenger);
            }
        }
    }

    public void showEntity(Player viewer, Entity target) {
        if (viewer == null || !viewer.isOnline() || target == null || !target.isValid()) {
            return;
        }

        Set<Integer> set = hiddenEntities.get(viewer.getUniqueId());
        if (set == null || !set.remove(target.getEntityId())) {
            return;
        }

        int entityId = target.getEntityId();
        Location loc = target.getLocation();
        UUID uuid = target.getUniqueId();

        EntityType packetEntityType = SpigotConversionUtil.fromBukkitEntityType(target.getType());
        if (packetEntityType == null) {
            packetEntityType = EntityTypes.PIG;
        }

        Vector3d position = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                packetEntityType,
                position,
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                0,
                Optional.empty()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);

        WrapperPlayServerEntityHeadLook headLook = new WrapperPlayServerEntityHeadLook(entityId, loc.getYaw());
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, headLook);

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

        List<Entity> passengers = target.getPassengers();
        if (!passengers.isEmpty()) {
            int[] passengerIds = new int[passengers.size()];
            for (int i = 0; i < passengers.size(); i++) {
                Entity p = passengers.get(i);
                passengerIds[i] = p.getEntityId();
                showEntity(viewer, p);
            }
            WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(entityId, passengerIds);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
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

        if (entity instanceof Projectile || entity instanceof FallingBlock || entity instanceof Item) {
            return false;
        }

        ConfigManager cfg = plugin.getConfigManager();
        if (entity instanceof Player) {
            return cfg.isHidePlayers();
        }
        if (entity instanceof Monster) {
            return cfg.isHideMonsters();
        }
        if (entity instanceof Animals || entity instanceof WaterMob) {
            return cfg.isHideAnimals();
        }
        return cfg.isHideMisc();
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
