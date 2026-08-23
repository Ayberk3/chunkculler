package dev.ayberk.subchunkculler;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public final class ReloadCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final ConfigManager config;

    public ReloadCommand(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("subchunkculler.admin")) {
            sender.sendMessage(ChatColor.RED + "Bu komutu kullanmak icin yetkiniz yok.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ChatColor.GOLD + "=== SubChunkCuller v" + plugin.getDescription().getVersion() + " ===");
            sender.sendMessage(ChatColor.YELLOW + "SubChunks Below: " + ChatColor.WHITE + config.getSubChunksBelow());
            sender.sendMessage(ChatColor.YELLOW + "Entity Hide Distance: " + ChatColor.WHITE + config.getHideDistanceY() + " blocks");
            sender.sendMessage(ChatColor.YELLOW + "Entity Show Distance: " + ChatColor.WHITE + config.getShowDistanceY() + " blocks");
            sender.sendMessage(ChatColor.GRAY + "Kullanim: /" + label + " reload");
            return true;
        }

        long start = System.currentTimeMillis();
        try {
            config.reload();
            plugin.startEntityTrackerTask();
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Config reload sirasinda hata olustu: " + e.getMessage());
            plugin.getLogger().warning("SubChunkCuller reload failed: " + e.getMessage());
            return true;
        }
        long took = System.currentTimeMillis() - start;

        sender.sendMessage(ChatColor.GREEN + "SubChunkCuller config yeniden yuklendi. (" + took + "ms)");
        sender.sendMessage(ChatColor.GRAY + "SubChunks Below=" + config.getSubChunksBelow()
                + ", Entity Hide Distance=" + config.getHideDistanceY() + " blocks");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
