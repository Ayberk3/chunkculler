package dev.ayberk.subchunkculler;

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
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            config.reload();
            plugin.startEntityTrackerTask();
            sender.sendMessage("§8[§bSubChunkCuller§8] §aReload complete! §7(SubChunks: §f"
                    + config.getSubChunksBelow() + "§7, Radius: §f"
                    + config.getRefreshRadius() + "§7, Entities: §f"
                    + config.isEntityCullerEnabled() + "§7, Fake Floor: §f"
                    + config.isFakeFloorEnabled() + "§7)");
            return true;
        }

        sender.sendMessage("§8[§bSubChunkCuller§8] §7Usage: §f/" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("subchunkculler.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return Collections.singletonList("reload");
            }
        }
        return Collections.emptyList();
    }
}
