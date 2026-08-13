package dev.ayberk.subchunkculler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public final class PlayerMoveListener implements Listener {

    private final Main plugin;
    private final ConfigManager config;
    private final EntityVisibilityListener visibility;

    public PlayerMoveListener(Main plugin, ConfigManager config, EntityVisibilityListener visibility) {
        this.plugin = plugin;
        this.config = config;
        this.visibility = visibility;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Main.PLAYER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
        visibility.refreshVisibilityAround(player);
        visibility.refreshLineOfSightFor(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Main.PLAYER_SECTION_Y.remove(id);
        Main.LAST_REFRESH.remove(id);
        visibility.clearPlayer(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        // Stale state from the world they just left could otherwise linger
        // and give a wrong result if they come back to it later.
        visibility.clearPlayer(id);
        Main.PLAYER_SECTION_Y.put(id, player.getLocation().getBlockY() >> 4);
        visibility.refreshVisibilityAround(player);
        visibility.refreshLineOfSightFor(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        handleSectionCheck(event.getPlayer(), event.getTo() == null ? null : event.getTo().getBlockY());
    }

    /**
     * Teleports (e.g. /home, /warp) can jump a player far horizontally
     * while staying in the SAME Y-section, or even the same Y entirely.
     * Unlike onMove, we can't gate this on a section change - that was the
     * actual bug behind players "leaking" right after teleporting home:
     * if Y-section didn't change, NOTHING re-evaluated their visibility at
     * the new location, so they kept whatever state applied at their
     * PREVIOUS spot. Always do a full refresh here.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Main.PLAYER_SECTION_Y.put(player.getUniqueId(), to.getBlockY() >> 4);

        if (!config.isWorldEnabled(to.getWorld().getName())) {
            return;
        }

        if (config.isDebugMode()) {
            plugin.getLogger().info(player.getName() + " teleported - forcing full visibility refresh");
        }

        refreshChunksAround(player);
        visibility.refreshVisibilityAround(player);
        visibility.refreshLineOfSightFor(player);
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

        if (newSection < oldSection) {
            refreshChunksAround(player);
        }

        visibility.refreshVisibilityAround(player);
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
