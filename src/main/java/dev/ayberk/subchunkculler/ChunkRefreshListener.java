package dev.ayberk.subchunkculler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkRefreshListener implements Listener {

    private final Main plugin;
    private final ConfigManager config;

    private final Map<UUID, Long> lastRefreshAt = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingDebounce = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSince = new ConcurrentHashMap<>();
    private static final long MAX_DEBOUNCE_MS = 1500L;

    private final ArrayDeque<long[]> refreshQueue = new ArrayDeque<>();
    private final Map<Long, World> chunkWorlds = new ConcurrentHashMap<>();
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
        for (BukkitTask task : pendingDebounce.values()) {
            task.cancel();
        }
        pendingDebounce.clear();
        pendingSince.clear();
        refreshQueue.clear();
        chunkWorlds.clear();
    }

    private void drainRefreshQueue() {
        int budget = config.getRefreshChunksPerTick();
        for (int i = 0; i < budget; i++) {
            long[] entry = refreshQueue.poll();
            if (entry == null) {
                return;
            }
            long worldKey = entry[0];
            int cx = (int) entry[1];
            int cz = (int) entry[2];

            World world = chunkWorlds.get(worldKey);
            if (world != null && world.isChunkLoaded(cx, cz)) {
                //noinspection deprecation
                world.refreshChunk(cx, cz);
            }
        }
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
        pendingSince.remove(id);

        BukkitTask task = pendingDebounce.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Main.VIEWER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
    }

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
            scheduleDebouncedRefresh(player);
        }
    }

    private void scheduleDebouncedRefresh(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        BukkitTask existing = pendingDebounce.get(id);
        Long since = pendingSince.get(id);
        if (existing != null && since != null && now - since < MAX_DEBOUNCE_MS) {
            existing.cancel();
        } else {
            pendingSince.put(id, now);
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingDebounce.remove(id);
            pendingSince.remove(id);
            if (player.isOnline()) {
                queueRefreshAround(player, player.getLocation());
            }
        }, config.getRefreshDebounceTicks());

        pendingDebounce.put(id, task);
    }

    private void refreshChunksAround(Player player, Location around) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastRefreshAt.get(id);
        if (last != null && now - last < config.getRefreshCooldownMs()) {
            return;
        }
        lastRefreshAt.put(id, now);
        queueRefreshAround(player, around);
    }

    private void queueRefreshAround(Player player, Location around) {
        World world = around.getWorld();
        long worldKey = world.getUID().getMostSignificantBits() ^ world.getUID().getLeastSignificantBits();
        chunkWorlds.putIfAbsent(worldKey, world);

        int centerX = around.getBlockX() >> 4;
        int centerZ = around.getBlockZ() >> 4;
        int radius = config.getRefreshRadius();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                if (world.isChunkLoaded(cx, cz)) {
                    refreshQueue.add(new long[]{worldKey, cx, cz});
                }
            }
        }
    }
}
