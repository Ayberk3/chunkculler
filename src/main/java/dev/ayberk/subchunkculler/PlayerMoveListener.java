package dev.ayberk.subchunkculler;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * Keeps {@link Main#PLAYER_SECTION_Y} up to date on the main thread, and -
 * whenever a player crosses downward into a new vertical sub-chunk section -
 * forces a resend of nearby chunks so the newly-uncovered section (they just
 * dug/moved one more sub-chunk down) is actually pushed to the client instead
 * of staying hidden until a natural chunk reload.
 *
 * Also keeps PLAYER-ENTITY visibility in sync with the terrain cutoff: chunk
 * data stripping alone does NOT stop the client from receiving spawn/move
 * packets for other players, so anyone standing below another player's
 * cutoff would otherwise be visible "floating" with no terrain around them -
 * exactly the ESP-style leak this plugin is supposed to prevent.
 */
public final class PlayerMoveListener implements Listener {

    private final Main plugin;
    private final ConfigManager config;

    public PlayerMoveListener(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Main.PLAYER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
        // Newly joined player might already be below/above the cutoff relative
        // to everyone already online (or vice versa) - sync both directions now.
        refreshVisibilityAround(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Main.PLAYER_SECTION_Y.remove(id);
        Main.LAST_REFRESH.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        handleSectionCheck(event.getPlayer(), event.getTo() == null ? null : event.getTo().getBlockY());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handleSectionCheck(event.getPlayer(), event.getTo() == null ? null : event.getTo().getBlockY());
    }

    private void handleSectionCheck(Player player, Integer toBlockY) {
        if (toBlockY == null || !config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        int newSection = toBlockY >> 4;
        UUID id = player.getUniqueId();
        Integer oldSection = Main.PLAYER_SECTION_Y.put(id, newSection);

        if (oldSection == null || oldSection.intValue() == newSection) {
            return;
        }

        if (config.isDebugMode()) {
            plugin.getLogger().info(player.getName() + " crossed sub-chunk section "
                    + oldSection + " -> " + newSection);
        }

        // Only need to force a re-push when the player revealed MORE world
        // (moved down) - moving up just raises the cutoff, and Minecraft's
        // normal chunk (re)loading already handles that direction fine.
        if (newSection < oldSection) {
            refreshChunksAround(player);
        }

        // Entity visibility has to be re-checked on BOTH directions (moving
        // down can hide this player from others above their new cutoff;
        // moving up can reveal this player again, or hide someone they can
        // no longer legitimately see below them).
        refreshVisibilityAround(player);
    }

    /**
     * Syncs player-entity visibility between {@code moved} and every other
     * online player in the same (enabled) world, using the exact same
     * cutoff formula the chunk packet stripper uses. For each direction we
     * hide the entity if its current section is strictly below the
     * viewer's cutoff section - mirrors the "actualSectionY < cutoffSection"
     * check in ChunkPacketListener so the two systems never disagree.
     */
    private void refreshVisibilityAround(Player moved) {
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

            // moved as the VIEWER looking at other as the TARGET
            int cutoffForMoved = config.computeCutoffSection(movedSection);
            if (otherSection < cutoffForMoved) {
                moved.hidePlayer(plugin, other);
            } else {
                moved.showPlayer(plugin, other);
            }

            // other as the VIEWER looking at moved as the TARGET
            int cutoffForOther = config.computeCutoffSection(otherSection);
            if (movedSection < cutoffForOther) {
                other.hidePlayer(plugin, moved);
            } else {
                other.showPlayer(plugin, moved);
            }
        }
    }

    private void refreshChunksAround(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = Main.LAST_REFRESH.get(id);
        if (last != null && now - last < config.getRefreshCooldownMs()) {
            return;
        }
        Main.LAST_REFRESH.put(id, now);

        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        int radius = config.getRefreshRadius();

        // World#refreshChunk() is deprecated on the Bukkit API but remains the
        // simplest cross-version way to force a resend to nearby players. Must
        // run on the main thread - this event already guarantees that.
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
