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

    /**
     * The ONLY per-viewer state this plugin keeps: which sub-chunk section
     * the viewer is currently standing in. It exists purely so
     * ChunkPacketListener knows where to cut terrain when it strips an
     * outgoing chunk packet - reading Bukkit's Player object directly from
     * inside the packet handler isn't safe, since packet events can fire
     * off the main thread. This is NOT a player/entity tracking system;
     * nothing else about the viewer (or any other player/entity) is stored
     * or inspected anywhere in this plugin.
     */
    public static final Map<UUID, Integer> VIEWER_SECTION_Y = new ConcurrentHashMap<>();

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

        PacketEvents.getAPI().getEventManager()
                .registerListener(new ChunkPacketListener(configManager));

        // Stripping the chunk packet only sanitises the first snapshot of a
        // chunk. This one keeps later block updates (flowing water, pistons,
        // crops, redstone, block breaking) from re-drawing the hidden area on
        // the client one packet at a time.
        PacketEvents.getAPI().getEventManager()
                .registerListener(new BlockUpdateListener(configManager));

        getServer().getPluginManager()
                .registerEvents(new ChunkRefreshListener(this, configManager), this);

        ReloadCommand reloadCommand = new ReloadCommand(this, configManager);
        PluginCommand command = getCommand("subchunkculler");
        if (command != null) {
            command.setExecutor(reloadCommand);
            command.setTabCompleter(reloadCommand);
        } else {
            getLogger().severe("subchunkculler command missing from plugin.yml - /subchunkculler reload will not work.");
        }

        // Covers the case where the plugin is /reload-ed while players are
        // already connected (no PlayerLoginEvent fires for them).
        for (Player player : getServer().getOnlinePlayers()) {
            VIEWER_SECTION_Y.put(player.getUniqueId(), player.getLocation().getBlockY() >> 4);
        }

        getLogger().info("SubChunkCuller enabled - hiding sections more than "
                + configManager.getSubChunksBelow() + " sub-chunk(s) below players.");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        VIEWER_SECTION_Y.clear();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
