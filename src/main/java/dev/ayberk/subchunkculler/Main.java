package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Main extends JavaPlugin {

    public static final Map<UUID, Integer> VIEWER_SECTION_Y = new ConcurrentHashMap<>();

    private ConfigManager configManager;
    private ChunkRefreshListener chunkRefreshListener;

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

        PacketEvents.getAPI().getEventManager()
                .registerListener(new ChunkPacketListener(configManager));

        PacketEvents.getAPI().getEventManager()
                .registerListener(new BlockUpdateListener(configManager));

        ChunkRefreshListener refreshListener = new ChunkRefreshListener(this, configManager);
        getServer().getPluginManager().registerEvents(refreshListener, this);
        refreshListener.startDrainTask();
        this.chunkRefreshListener = refreshListener;

        ReloadCommand reloadCommand = new ReloadCommand(this, configManager);
        PluginCommand command = getCommand("subchunkculler");
        if (command != null) {
            command.setExecutor(reloadCommand);
            command.setTabCompleter(reloadCommand);
        } else {
            getLogger().severe("subchunkculler command missing from plugin.yml - /subchunkculler reload will not work.");
        }

        for (Player player : getServer().getOnlinePlayers()) {
            VIEWER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
        }

        getLogger().info("SubChunkCuller enabled - hiding sections more than "
                + configManager.getSubChunksBelow() + " sub-chunk(s) below players.");
    }

    @Override
    public void onDisable() {
        if (chunkRefreshListener != null) {
            chunkRefreshListener.stop();
        }
        PacketEvents.getAPI().terminate();
        VIEWER_SECTION_Y.clear();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
