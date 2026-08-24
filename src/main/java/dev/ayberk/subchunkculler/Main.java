package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Main extends JavaPlugin {

    public static final Map<UUID, Integer> VIEWER_SECTION_Y = new ConcurrentHashMap<>();
    
    // Tracks which players were hidden explicitly by our plugin
    private static final Map<UUID, java.util.Set<UUID>> CULLED_PLAYERS = new ConcurrentHashMap<>();

    private ConfigManager configManager;
    private ChunkRefreshListener chunkRefreshListener;
    private EntityPacketListener entityPacketListener;
    private ChunkPacketListener chunkPacketListener;
    private BlockUpdateListener blockUpdateListener;
    private EntityTrackerTask trackerRunnable;
    private BukkitTask entityTrackerTask;

    public static void addCulledPlayer(UUID viewer, UUID target) {
        CULLED_PLAYERS.computeIfAbsent(viewer, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(target);
    }

    public static void removeCulledPlayer(UUID viewer, UUID target) {
        java.util.Set<UUID> targets = CULLED_PLAYERS.get(viewer);
        if (targets != null) {
            targets.remove(target);
        }
    }

    public static boolean isPlayerCulledByUs(UUID viewer, UUID target) {
        java.util.Set<UUID> targets = CULLED_PLAYERS.get(viewer);
        return targets != null && targets.contains(target);
    }

    public static void clearCulledForViewer(UUID viewer) {
        CULLED_PLAYERS.remove(viewer);
    }

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .checkForUpdates(false)
                .bStats(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();
        ChunkResender.init();

        // 1. Config Manager
        this.configManager = new ConfigManager(this);
        configManager.load();
        getServer().getPluginManager().registerEvents(configManager, this);

        // 2. SubChunk Packet Listeners
        this.chunkPacketListener = new ChunkPacketListener(configManager);
        this.blockUpdateListener = new BlockUpdateListener(configManager);
        PacketEvents.getAPI().getEventManager().registerListener(chunkPacketListener);
        PacketEvents.getAPI().getEventManager().registerListener(blockUpdateListener);

        // 3. Chunk Refresh & Player Lifecycle Listener
        this.chunkRefreshListener = new ChunkRefreshListener(this, configManager);
        getServer().getPluginManager().registerEvents(chunkRefreshListener, this);
        chunkRefreshListener.startDrainTask();

        // 4. Entity Packet Listener & Native Entity Tracker
        this.entityPacketListener = new EntityPacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(entityPacketListener);
        startEntityTrackerTask();

        // 5. Admin Commands
        ReloadCommand reloadCommand = new ReloadCommand(this, configManager);
        PluginCommand command = getCommand("subchunkculler");
        if (command != null) {
            command.setExecutor(reloadCommand);
            command.setTabCompleter(reloadCommand);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            VIEWER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
        }

        getLogger().info("SubChunkCuller v" + getDescription().getVersion() + " successfully enabled!");
    }

    public void startEntityTrackerTask() {
        if (entityTrackerTask != null && !entityTrackerTask.isCancelled()) {
            entityTrackerTask.cancel();
        }
        if (configManager.isEntityCullerEnabled()) {
            this.trackerRunnable = new EntityTrackerTask(this);
            int interval = configManager.getCheckIntervalTicks();
            this.entityTrackerTask = trackerRunnable.runTaskTimer(this, 20L, interval);
        }
    }

    public void updatePlayerEntities(Player player) {
        if (trackerRunnable != null && player != null && player.isOnline()) {
            trackerRunnable.updatePlayer(player);
        }
    }

    @Override
    public void onDisable() {
        if (chunkRefreshListener != null) {
            chunkRefreshListener.stop();
        }
        if (entityTrackerTask != null && !entityTrackerTask.isCancelled()) {
            entityTrackerTask.cancel();
        }

        // BUG-9 FIX: Show all hidden entities before disabling
        for (Player viewer : getServer().getOnlinePlayers()) {
            for (Entity entity : viewer.getWorld().getEntities()) {
                if (entity != null && entity.isValid() && !viewer.canSee(entity)) {
                    try {
                        viewer.showEntity(this, entity);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        if (chunkPacketListener != null) {
            chunkPacketListener.clearCache();
            PacketEvents.getAPI().getEventManager().unregisterListener(chunkPacketListener);
        }
        if (blockUpdateListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(blockUpdateListener);
        }
        if (entityPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(entityPacketListener);
        }

        PacketEvents.getAPI().terminate();
        VIEWER_SECTION_Y.clear();
        getLogger().info("SubChunkCuller disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
