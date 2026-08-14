package dev.ayberk.subchunkculler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure chunk-resend plumbing. Nothing here inspects, hides, or tracks any
 * other player/entity - its only two jobs are:
 *   1. keep Main.VIEWER_SECTION_Y up to date so ChunkPacketListener knows
 *      where to cut terrain for the current viewer;
 *   2. force a chunk resend when the viewer digs downward across a
 *      sub-chunk section boundary, or teleports, so newly-uncovered
 *      terrain actually reaches their client instead of staying stripped.
 *
 * VIEWER_SECTION_Y is seeded on PlayerLoginEvent specifically (not
 * PlayerJoinEvent) because Paper can start streaming a joining player's
 * first chunk packets before PlayerJoinEvent fires. PlayerLoginEvent fires
 * earlier, before the player is placed in the world, so by the time the
 * first CHUNK_DATA packet goes out the cutoff is already known and that
 * initial burst gets stripped correctly instead of leaking unprotected.
 */
public final class ChunkRefreshListener implements Listener {

    private final Main plugin;
    private final ConfigManager config;

    private final Map<UUID, Long> lastRefreshAt = new ConcurrentHashMap<>();

    public ChunkRefreshListener(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Main.VIEWER_SECTION_Y.remove(id);
        lastRefreshAt.remove(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
    }

    /**
     * Death can move a player far from where they were (e.g. dug deep
     * underground, respawn back at the surface bed/spawn point). Without
     * this, the stale pre-death section would be used to compute the
     * cutoff for the respawn location's first chunk packets.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location respawnLocation = event.getRespawnLocation();
        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), respawnLocation.getBlockY() >> 4);

        if (!config.isWorldEnabled(respawnLocation.getWorld().getName())) {
            return;
        }
        refreshChunksAround(player, respawnLocation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        handleSectionCheck(event.getPlayer(), event.getTo());
    }

    /**
     * Teleports (e.g. /home, /warp, portals) can jump a player far
     * horizontally while staying in the same Y-section, or even the same Y
     * entirely - and can also be how a player changes worlds. Unlike
     * onMove, we can't gate this on a section change - always force a
     * refresh so newly-relevant chunks around the destination load in.
     * PlayerTeleportEvent fires BEFORE the teleport happens, so this also
     * closes the same kind of race PlayerLoginEvent closes for joining.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), to.getBlockY() >> 4);

        if (!config.isWorldEnabled(to.getWorld().getName())) {
            return;
        }

        if (config.isDebugMode()) {
            plugin.getLogger().info(player.getName() + " teleported - forcing full chunk refresh");
        }

        refreshChunksAround(player, to);
    }

    private void handleSectionCheck(Player player, Location to) {
        if (to == null || !config.isWorldEnabled(to.getWorld().getName())) {
            return;
        }

        int newSection = to.getBlockY() >> 4;
        UUID id = player.getUniqueId();
        Integer oldSection = Main.VIEWER_SECTION_Y.put(id, newSection);

        if (oldSection == null || oldSection.intValue() == newSection) {
            return;
        }

        if (config.isDebugMode()) {
            plugin.getLogger().info(player.getName() + " crossed sub-chunk section "
                    + oldSection + " -> " + newSection);
        }

        if (newSection < oldSection) {
            refreshChunksAround(player, to);
        }
    }

    private void refreshChunksAround(Player player, Location around) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastRefreshAt.get(id);
        if (last != null && now - last < config.getRefreshCooldownMs()) {
            return;
        }
        lastRefreshAt.put(id, now);

        World world = around.getWorld();
        int centerX = around.getBlockX() >> 4;
        int centerZ = around.getBlockZ() >> 4;
        int radius = config.getRefreshRadius();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                if (world.isChunkLoaded(cx, cz)) {
                    //noinspection deprecation
                    world.refreshChunk(cx, cz);
                }
            }
        }
    }
}
