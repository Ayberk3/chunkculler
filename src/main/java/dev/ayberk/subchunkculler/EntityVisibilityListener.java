package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controls per-viewer PLAYER-ENTITY visibility purely at the packet level -
 * cancels/fakes only the Spawn/Destroy Entity packets. Deliberately does
 * NOT use Player#hidePlayer()/showPlayer(): those tie into Bukkit/Paper's
 * "hidden players" bookkeeping, which also silently strips the target from
 * tab-complete suggestions (chat @-mentions, /msg <tab>, etc.) and tab list.
 * We only want the in-world model gone - nothing else about the player
 * should look any different to viewers.
 *
 * Known limitation: while hidden, pose/metadata updates (sneaking, elytra,
 * held item, etc.) for the target aren't tracked, so on re-appearing they
 * may show the pose they had at the moment they were hidden until their
 * next natural metadata update. Purely cosmetic, not a visibility leak.
 */
public final class EntityVisibilityListener extends PacketListenerAbstract {

    private final Plugin plugin;
    private final ConfigManager config;

    // viewerUUID -> set of targetUUIDs whose entity is currently suppressed
    // (spawn cancelled / destroy sent) on that viewer's client.
    private final Map<UUID, Set<UUID>> hiddenFrom = new ConcurrentHashMap<>();

    public EntityVisibilityListener(Plugin plugin, ConfigManager config) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SPAWN_ENTITY) {
            return;
        }

        Object rawViewer = event.getPlayer();
        if (!(rawViewer instanceof Player viewer)) {
            return;
        }
        if (!config.isWorldEnabled(viewer.getWorld().getName())) {
            return;
        }

        WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
        if (wrapper.getEntityType() != EntityTypes.PLAYER || wrapper.getUUID().isEmpty()) {
            return;
        }

        UUID targetId = wrapper.getUUID().get();
        if (targetId.equals(viewer.getUniqueId())) {
            return;
        }

        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null) {
            return;
        }

        Integer viewerSection = Main.PLAYER_SECTION_Y.get(viewer.getUniqueId());
        Integer targetSection = Main.PLAYER_SECTION_Y.get(targetId);
        if (viewerSection == null || targetSection == null) {
            return;
        }

        int cutoff = config.computeCutoffSection(viewerSection);
        if (targetSection < cutoff) {
            event.setCancelled(true);
            markHidden(viewer.getUniqueId(), targetId);
        } else {
            markVisible(viewer.getUniqueId(), targetId);
        }
    }

    /**
     * Called from PlayerMoveListener whenever {@code moved} crosses a
     * sub-chunk section boundary. Re-checks visibility both ways between
     * {@code moved} and every other online player in the same world.
     */
    public void refreshVisibilityAround(Player moved) {
        World world = moved.getWorld();
        if (!config.isWorldEnabled(world.getName())) {
            return;
        }

        Integer movedSection = Main.PLAYER_SECTION_Y.get(moved.getUniqueId());
        if (movedSection == null) {
            return;
        }

        for (Player other : world.getPlayers()) {
            if (other.equals(moved)) {
                continue;
            }
            Integer otherSection = Main.PLAYER_SECTION_Y.get(other.getUniqueId());
            if (otherSection == null) {
                continue;
            }

            int cutoffForMoved = config.computeCutoffSection(movedSection);
            applyVisibility(moved, other, otherSection >= cutoffForMoved);

            int cutoffForOther = config.computeCutoffSection(otherSection);
            applyVisibility(other, moved, movedSection >= cutoffForOther);
        }
    }

    public void clearPlayer(UUID id) {
        hiddenFrom.remove(id);
        for (Set<UUID> set : hiddenFrom.values()) {
            set.remove(id);
        }
    }

    private void applyVisibility(Player viewer, Player target, boolean shouldBeVisible) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();
        boolean currentlyHidden = isHidden(viewerId, targetId);

        if (shouldBeVisible && currentlyHidden) {
            respawn(viewer, target);
            markVisible(viewerId, targetId);
        } else if (!shouldBeVisible && !currentlyHidden) {
            destroy(viewer, target);
            markHidden(viewerId, targetId);
        }
    }

    private void destroy(Player viewer, Player target) {
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(target.getEntityId());
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private void respawn(Player viewer, Player target) {
        Location loc = target.getLocation();
        Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());

        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                target.getEntityId(),
                Optional.of(target.getUniqueId()),
                EntityTypes.PLAYER,
                pos,
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                0,
                Optional.empty()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private boolean isHidden(UUID viewer, UUID target) {
        return hiddenFrom.getOrDefault(viewer, Collections.emptySet()).contains(target);
    }

    private void markHidden(UUID viewer, UUID target) {
        hiddenFrom.computeIfAbsent(viewer, k -> ConcurrentHashMap.newKeySet()).add(target);
    }

    private void markVisible(UUID viewer, UUID target) {
        Set<UUID> set = hiddenFrom.get(viewer);
        if (set != null) {
            set.remove(target);
        }
    }
}
