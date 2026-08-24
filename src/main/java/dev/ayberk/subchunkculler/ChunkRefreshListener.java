package dev.ayberk.subchunkculler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ChunkRefreshListener implements Listener {

    private final Main plugin;
    private final ConfigManager config;

    // BUG-3 FIX: Queue size cap to prevent OOM
    private static final int MAX_QUEUE_SIZE = 5000;

    private static final class RefreshEntry {
        final Player player;
        final World world;
        final int cx;
        final int cz;

        RefreshEntry(Player player, World world, int cx, int cz) {
            this.player = player;
            this.world = world;
            this.cx = cx;
            this.cz = cz;
        }
    }

    private final ConcurrentLinkedQueue<RefreshEntry> refreshQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask drainTask;

    public ChunkRefreshListener(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void startDrainTask() {
        if (drainTask != null) {
            return;
        }
        drainTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drainRefreshQueue, 1L, 1L);
    }

    public void stop() {
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        refreshQueue.clear();
    }

    private void drainRefreshQueue() {
        int budget = config.getRefreshChunksPerTick();
        for (int i = 0; i < budget; i++) {
            RefreshEntry entry = refreshQueue.poll();
            if (entry == null) {
                return;
            }

            Player player = entry.player;
            if (player != null && player.isOnline() && entry.world.isChunkLoaded(entry.cx, entry.cz)) {
                boolean sent = ChunkResender.resendChunk(player, entry.world, entry.cx, entry.cz);
                if (!sent) {
                    //noinspection deprecation
                    entry.world.refreshChunk(entry.cx, entry.cz);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        if (event.getSpawnLocation() != null) {
            Main.VIEWER_SECTION_Y.put(event.getPlayer().getUniqueId(), event.getSpawnLocation().getBlockY() >> 4);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int realSection = player.getLocation().getBlockY() >> 4;
        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), realSection);
        plugin.updatePlayerEntities(player);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && config.isWorldEnabled(player.getWorld().getName())) {
                queueRefreshAround(player, player.getLocation());
                plugin.updatePlayerEntities(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Main.VIEWER_SECTION_Y.remove(id);
        refreshQueue.removeIf(entry -> entry.player.getUniqueId().equals(id));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
        plugin.updatePlayerEntities(player);
        if (config.isWorldEnabled(player.getWorld().getName())) {
            queueRefreshAround(player, player.getLocation());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location respawnLocation = event.getRespawnLocation();
        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), respawnLocation.getBlockY() >> 4);

        if (!config.isWorldEnabled(respawnLocation.getWorld().getName())) {
            return;
        }
        plugin.updatePlayerEntities(player);
        queueRefreshAround(player, respawnLocation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        handleSectionCheck(event.getPlayer(), event.getTo());
    }

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

        plugin.updatePlayerEntities(player);
        queueRefreshAround(player, to);
    }

    private void handleSectionCheck(Player player, Location to) {
        if (to == null || !config.isWorldEnabled(to.getWorld().getName())) {
            return;
        }

        int newSection = to.getBlockY() >> 4;
        UUID id = player.getUniqueId();
        Integer oldSection = Main.VIEWER_SECTION_Y.put(id, newSection);

        if (oldSection == null) {
            return;
        }

        if (newSection != oldSection.intValue()) {
            if (config.isDebugMode()) {
                plugin.getLogger().info(player.getName() + " crossed sub-chunk section "
                        + oldSection + " -> " + newSection);
            }

            // Immediately update entities and queue chunks (sorted by distance) to prevent falling into the void.
            plugin.updatePlayerEntities(player);
            queueRefreshAround(player, to);
        }
    }

    private void queueRefreshAround(Player player, Location around) {
        // Remove stale refresh tasks for this player to prevent queue flooding when falling rapidly
        refreshQueue.removeIf(entry -> entry.player.getUniqueId().equals(player.getUniqueId()));

        World world = around.getWorld();
        int centerX = around.getBlockX() >> 4;
        int centerZ = around.getBlockZ() >> 4;

        int viewDist = 8;
        try {
            viewDist = player.getClientViewDistance();
        } catch (Throwable ignored) {
        }
        int radius = Math.max(config.getRefreshRadius(), Math.min(viewDist, 12));

        List<RefreshEntry> newEntries = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                if (world.isChunkLoaded(cx, cz)) {
                    newEntries.add(new RefreshEntry(player, world, cx, cz));
                }
            }
        }

        // Sort chunks so the chunk the player is standing in (and immediate neighbors) are refreshed FIRST
        newEntries.sort(Comparator.comparingInt(e -> {
            int dx = e.cx - centerX;
            int dz = e.cz - centerZ;
            return dx * dx + dz * dz;
        }));

        for (RefreshEntry entry : newEntries) {
            if (refreshQueue.size() >= MAX_QUEUE_SIZE) {
                break;
            }
            refreshQueue.add(entry);
        }
    }
}
