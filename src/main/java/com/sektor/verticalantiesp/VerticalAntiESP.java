package com.sektor.verticalantiesp;

import com.github.retrooper.packetevents.PacketEvents;
import com.sektor.verticalantiesp.config.ConfigManager;
import com.sektor.verticalantiesp.listener.EventListener;
import com.sektor.verticalantiesp.manager.EntityTrackingManager;
import com.sektor.verticalantiesp.packet.PacketManager;
import com.sektor.verticalantiesp.task.EntityTrackerTask;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class VerticalAntiESP extends JavaPlugin implements CommandExecutor {

    private static VerticalAntiESP instance;
    private ConfigManager configManager;
    private EntityTrackingManager trackingManager;
    private PacketManager packetManager;
    private BukkitTask trackerTask;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Load Configuration
        this.configManager = new ConfigManager(this);

        // 2. Initialize Managers
        this.trackingManager = new EntityTrackingManager(this);
        this.packetManager = new PacketManager(this);

        // 3. Register PacketEvents Listener
        PacketEvents.getAPI().getEventManager().registerListener(this.packetManager);

        // 4. Register Bukkit Events
        getServer().getPluginManager().registerEvents(new EventListener(this), this);

        // 5. Register Command
        if (getCommand("verticalantiesp") != null) {
            getCommand("verticalantiesp").setExecutor(this);
        }

        // 6. Start Tracking Task
        startTrackerTask();

        getLogger().info("VerticalAntiESP v" + getDescription().getVersion() + " successfully enabled!");
        getLogger().info("Zero-leak vertical entity culling active (Hide: " +
                configManager.getHideDistanceY() + " blocks, Show: " +
                configManager.getShowDistanceY() + " blocks).");
    }

    @Override
    public void onDisable() {
        if (trackerTask != null && !trackerTask.isCancelled()) {
            trackerTask.cancel();
        }

        if (packetManager != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetManager);
        }

        if (trackingManager != null) {
            trackingManager.clearAll();
        }

        getLogger().info("VerticalAntiESP disabled.");
    }

    public void startTrackerTask() {
        if (trackerTask != null && !trackerTask.isCancelled()) {
            trackerTask.cancel();
        }
        int interval = configManager.getCheckIntervalTicks();
        this.trackerTask = new EntityTrackerTask(this).runTaskTimer(this, 20L, interval);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("verticalantiesp.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            configManager.loadConfig();
            startTrackerTask();
            sender.sendMessage(ChatColor.GREEN + "[VerticalAntiESP] Configuration reloaded successfully!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== VerticalAntiESP v" + getDescription().getVersion() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Hide Distance: " + ChatColor.WHITE + configManager.getHideDistanceY() + " blocks");
        sender.sendMessage(ChatColor.YELLOW + "Show Distance: " + ChatColor.WHITE + configManager.getShowDistanceY() + " blocks");
        sender.sendMessage(ChatColor.YELLOW + "Check Interval: " + ChatColor.WHITE + configManager.getCheckIntervalTicks() + " ticks");
        sender.sendMessage(ChatColor.GRAY + "Use /verticalantiesp reload to reload configuration.");
        return true;
    }

    public static VerticalAntiESP getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EntityTrackingManager getTrackingManager() {
        return trackingManager;
    }
}
