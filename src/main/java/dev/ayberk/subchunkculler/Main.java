package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Main extends JavaPlugin {

    public static final Map<UUID, Integer> PLAYER_SECTION_Y = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LAST_REFRESH = new ConcurrentHashMap<>();

    private ConfigManager configManager;

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

        this.configManager = new ConfigManager(this);
        configManager.load();
        getServer().getPluginManager().registerEvents(configManager, this);

        EntityVisibilityListener visibilityListener = new EntityVisibilityListener(this, configManager);

        PacketEvents.getAPI().getEventManager()
                .registerListener(new ChunkPacketListener(configManager));
        PacketEvents.getAPI().getEventManager()
                .registerListener(visibilityListener);

        getServer().getPluginManager()
                .registerEvents(new PlayerMoveListener(this, configManager, visibilityListener), this);

        ReloadCommand reloadCommand = new ReloadCommand(this, configManager);
        getCommand("subchunkculler").setExecutor(reloadCommand);
        getCommand("subchunkculler").setTabCompleter(reloadCommand);

        getServer().getScheduler().runTaskTimer(
                this,
                visibilityListener::refreshLineOfSight,
                configManager.getLosCheckIntervalTicks(),
                configManager.getLosCheckIntervalTicks()
        );

        for (Player player : getServer().getOnlinePlayers()) {
            PLAYER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
        }

        getLogger().info("SubChunkCuller enabled - hiding sections more than "
                + configManager.getSubChunksBelow() + " sub-chunk(s) below players.");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        PLAYER_SECTION_Y.clear();
        LAST_REFRESH.clear();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
