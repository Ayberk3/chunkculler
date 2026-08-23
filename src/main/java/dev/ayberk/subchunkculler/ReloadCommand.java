package dev.ayberk.subchunkculler;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
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
            sender.sendMessage(ChatColor.RED + "You don't have permission to execute this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            config.reload();
            plugin.startEntityTrackerTask();

            sender.sendMessage(ChatColor.GREEN + "[SubChunkCuller] Config reloaded successfully!");
            sender.sendMessage(ChatColor.GRAY + " - SubChunks Below: " + ChatColor.YELLOW + config.getSubChunksBelow());
            sender.sendMessage(ChatColor.GRAY + " - Absolute Cutoff Y: " + ChatColor.YELLOW + (config.getAbsoluteCutoffSection() << 4));
            sender.sendMessage(ChatColor.GRAY + " - Fake Floor: " + ChatColor.YELLOW + config.isFakeFloorEnabled() + " (" + config.getFakeFloorBlock() + ")");
            sender.sendMessage(ChatColor.GRAY + " - Entity Culling: " + ChatColor.YELLOW + (config.isEntityCullerEnabled() ? "Enabled" : "Disabled"));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== SubChunkCuller v" + plugin.getDescription().getVersion() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload " + ChatColor.GRAY + "- Reload configuration");
        sender.sendMessage(ChatColor.GRAY + "Status: SubChunks Below=" + config.getSubChunksBelow()
                + ", Entity Culler=" + (config.isEntityCullerEnabled() ? "ON" : "OFF"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("subchunkculler.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
