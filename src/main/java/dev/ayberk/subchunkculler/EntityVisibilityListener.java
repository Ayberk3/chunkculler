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
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Controls per-viewer PLAYER-ENTITY visibility purely at the packet level -
 * cancels/fakes only the Spawn/Destroy Entity packets. Deliberately does
 * NOT use Player#hidePlayer()/showPlayer(): those also strip tab-complete
 * and tab list.
 *
 * Two independent reasons can hide a target from a viewer:
 *  - Y-CUTOFF: same rule ChunkPacketListener uses for terrain.
 *  - LINE-OF-SIGHT: applies everywhere. If solid terrain blocks a straight
 *    line between viewer and target, or they're farther than max-distance,
 *    the target is hidden.
 *
 * IMPORTANT THREADING NOTE: PacketEvents may call onPacketSend() off the
 * main thread for some packet types. World/raytrace access is NOT safe
 * there. So the LOS raytrace NEVER happens inside onPacketSend() - it only
 * happens in refreshLineOfSight()/refreshLineOfSightFor(), both of which
 * are only ever called from main-thread contexts (Bukkit scheduler task,
 * Bukkit event handlers). onPacketSend() only reads already-computed,
 * thread-safe cached state.
 */
public final class EntityVisibilityListener extends PacketListenerAbstract {

    private final Plugin plugin;
    private final ConfigManager config;

    private final Map<UUID, Set<UUID>> yCutoffHidden = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> losHidden = new ConcurrentHashMap<>();

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
        UUID viewerId = viewer.getUniqueId();
        if (targetId.equals(viewerId)) {
            return;
        }

        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null) {
            return;
        }

        // Y-cutoff: cheap, only touches the concurrent PLAYER_SECTION_Y map
        // and plain arithmetic - safe from any thread.
        boolean hiddenByCutoff = computeYCutoffHidden(viewer, target);
        setFlag(yCutoffHidden, viewerId, targetId, hiddenByCutoff);

        // LOS: NEVER raytrace here (thread safety). Only consult the cache
        // that refreshLineOfSight()/refreshLineOfSightFor() maintain on the
        // main thread. Worst case for a brand-new pair that hasn't been
        // scanned yet: briefly visible until the next periodic sweep or the
        // next join/teleport/world-change event closes the gap.
        boolean hiddenByLos = config.isLosEnabled() && isFlagSet(losHidden, viewerId, targetId);

        if (hiddenByCutoff || hiddenByLos) {
            event.setCancelled(true);
        }
    }

    /**
     * Y-cutoff refresh. Called whenever {@code moved} crosses a sub-chunk
     * section boundary, or right after a teleport/join/world-change.
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
            updateYCutoff(moved, other, otherSection < cutoffForMoved);

            int cutoffForOther = config.computeCutoffSection(otherSection);
            updateYCutoff(other, moved, movedSection < cutoffForOther);
        }
    }

    /**
     * Full O(n^2) LOS sweep across every online pair. Called periodically
     * from Main's scheduled task (main thread only).
     */
    public void refreshLineOfSight() {
        if (!config.isLosEnabled()) {
            return;
        }

        for (World world : plugin.getServer().getWorlds()) {
            if (!config.isWorldEnabled(world.getName())) {
                continue;
            }

            List<Player> players = new ArrayList<>(world.getPlayers());
            int size = players.size();
            for (int i = 0; i < size; i++) {
                Player a = players.get(i);
                if (!a.isOnline()) {
                    continue;
                }
                for (int j = i + 1; j < size; j++) {
                    Player b = players.get(j);
                    if (!b.isOnline()) {
                        continue;
                    }
                    boolean blocked = isLosBlocked(a, b);
                    updateLos(a, b, blocked);
                    updateLos(b, a, blocked);
                }
            }
        }
    }

    /**
     * Targeted O(n) LOS check for just one player against everyone else in
     * their world. Called from join/teleport/world-change handlers so
     * these relatively rare, "big position jump" events get an IMMEDIATE
     * correct result instead of waiting up to check-interval-ticks for the
     * next full sweep. This is what makes /home teleports react instantly.
     */
    public void refreshLineOfSightFor(Player moved) {
        if (!config.isLosEnabled()) {
            return;
        }
        World world = moved.getWorld();
        if (!config.isWorldEnabled(world.getName())) {
            return;
        }
        if (!moved.isOnline()) {
            return;
        }

        for (Player other : world.getPlayers()) {
            if (other.equals(moved) || !other.isOnline()) {
                continue;
            }
            boolean blocked = isLosBlocked(moved, other);
            updateLos(moved, other, blocked);
            updateLos(other, moved, blocked);
        }
    }

    /** Wipes all hidden-state bookkeeping involving this player, in either direction. */
    public void clearPlayer(UUID id) {
        yCutoffHidden.remove(id);
        losHidden.remove(id);
        for (Set<UUID> set : yCutoffHidden.values()) {
            set.remove(id);
        }
        for (Set<UUID> set : losHidden.values()) {
            set.remove(id);
        }
    }

    private boolean computeYCutoffHidden(Player viewer, Player target) {
        Integer viewerSection = Main.PLAYER_SECTION_Y.get(viewer.getUniqueId());
        Integer targetSection = Main.PLAYER_SECTION_Y.get(target.getUniqueId());
        if (viewerSection == null || targetSection == null) {
            return false;
        }
        int cutoff = config.computeCutoffSection(viewerSection);
        return targetSection < cutoff;
    }

    /**
     * True if solid terrain blocks a straight line between the two
     * players' eyes, or they're farther apart than max-distance. Beyond
     * max-distance we return false on purpose - Paper's own entity
     * tracking range already stops sending packets that far out, we don't
     * need to manage it.
     *
     * MAIN THREAD ONLY - do not call from onPacketSend or any async context.
     */
    private boolean isLosBlocked(Player a, Player b) {
        if (!a.isOnline() || !b.isOnline()) {
            return false;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            return false;
        }

        Location eyeA = a.getEyeLocation();
        Location eyeB = b.getEyeLocation();

        double maxDistance = config.getLosMaxDistance();
        double distanceSq = eyeA.distanceSquared(eyeB);
        if (distanceSq > maxDistance * maxDistance) {
            return false;
        }

        double distance = Math.sqrt(distanceSq);
        if (distance < 0.5) {
            return false;
        }

        try {
            Vector direction = eyeB.toVector().subtract(eyeA.toVector()).normalize();
            RayTraceResult hit = eyeA.getWorld().rayTraceBlocks(
                    eyeA, direction, distance - 0.3, FluidCollisionMode.NEVER, true);
            return hit != null;
        } catch (Exception e) {
            // Never let a raytrace hiccup (unloaded chunk edge case, etc.)
            // take the listener down or spam the console every tick.
            if (config.isDebugMode()) {
                plugin.getLogger().log(Level.WARNING, "LOS raytrace failed between "
                        + a.getName() + " and " + b.getName(), e);
            }
            return false;
        }
    }

    private void updateYCutoff(Player viewer, Player target, boolean hidden) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();
        boolean wasEffective = isEffectivelyHidden(viewerId, targetId);
        setFlag(yCutoffHidden, viewerId, targetId, hidden);
        boolean nowEffective = isEffectivelyHidden(viewerId, targetId);
        reconcile(viewer, target, wasEffective, nowEffective);
    }

    private void updateLos(Player viewer, Player target, boolean hidden) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();
        boolean wasEffective = isEffectivelyHidden(viewerId, targetId);
        setFlag(losHidden, viewerId, targetId, hidden);
        boolean nowEffective = isEffectivelyHidden(viewerId, targetId);
        reconcile(viewer, target, wasEffective, nowEffective);
    }

    private void reconcile(Player viewer, Player target, boolean wasEffectivelyHidden, boolean nowEffectivelyHidden) {
        if (wasEffectivelyHidden == nowEffectivelyHidden) {
            return;
        }
        if (!viewer.isOnline() || !target.isOnline()) {
            return;
        }
        if (nowEffectivelyHidden) {
            destroy(viewer, target);
        } else {
            respawn(viewer, target);
        }
    }

    private boolean isEffectivelyHidden(UUID viewer, UUID target) {
        return isFlagSet(yCutoffHidden, viewer, target) || isFlagSet(losHidden, viewer, target);
    }

    private boolean isFlagSet(Map<UUID, Set<UUID>> map, UUID viewer, UUID target) {
        Set<UUID> set = map.get(viewer);
        return set != null && set.contains(target);
    }

    private void setFlag(Map<UUID, Set<UUID>> map, UUID viewer, UUID target, boolean value) {
        if (value) {
            map.computeIfAbsent(viewer, k -> ConcurrentHashMap.newKeySet()).add(target);
        } else {
            Set<UUID> set = map.get(viewer);
            if (set != null) {
                set.remove(target);
            }
        }
    }

    private void destroy(Player viewer, Player target) {
        try {
            WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(target.getEntityId());
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        } catch (Exception e) {
            if (config.isDebugMode()) {
                plugin.getLogger().log(Level.WARNING, "Failed to send destroy packet for "
                        + target.getName() + " to " + viewer.getName(), e);
            }
        }
    }

    private void respawn(Player viewer, Player target) {
        try {
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
        } catch (Exception e) {
            if (config.isDebugMode()) {
                plugin.getLogger().log(Level.WARNING, "Failed to send respawn packet for "
                        + target.getName() + " to " + viewer.getName(), e);
            }
        }
    }
}
