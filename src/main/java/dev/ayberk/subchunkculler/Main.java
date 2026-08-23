package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Main extends JavaPlugin {

    public static final Map<UUID, Integer> VIEWER_SECTION_Y = new ConcurrentHashMap<>();

    private ConfigManager configManager;
    private ChunkRefreshListener chunkRefreshListener;
    private EntityTrackingManager entityTrackingManager;
    private EntityPacketListener entityPacketListener;
    private ChunkPacketListener chunkPacketListener;
    private BlockUpdateListener blockUpdateListener;
    private EntityTrackerTask trackerRunnable;
    private BukkitTask entityTrackerTask;

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

        // 3. Chunk Refresh Listener
        this.chunkRefreshListener = new ChunkRefreshListener(this, configManager);
        getServer().getPluginManager().registerEvents(chunkRefreshListener, this);
        chunkRefreshListener.startDrainTask();

        // 4. Entity Tracking & Zero-Leak Anti-ESP
        this.entityTrackingManager = new EntityTrackingManager(this);
        this.entityPacketListener = new EntityPacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(entityPacketListener);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        startEntityTrackerTask();

        // 5. Commands
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
        getLogger().info("SubChunks Below: " + configManager.getSubChunksBelow() +
                ", Dynamic Void Entity Culling active.");
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
        if (chunkPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(chunkPacketListener);
        }
        if (blockUpdateListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(blockUpdateListener);
        }
        if (entityPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(entityPacketListener);
        }
        if (entityTrackingManager != null) {
            entityTrackingManager.clearAll();
        }

        PacketEvents.getAPI().terminate();
        VIEWER_SECTION_Y.clear();
        getLogger().info("SubChunkCuller disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EntityTrackingManager getEntityTrackingManager() {
        return entityTrackingManager;
    }
}
