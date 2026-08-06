package dev.ayberk.subchunkculler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityVisibilityTask extends BukkitRunnable implements Listener {

    private final Main plugin;
    private final ConfigManager config;

    private final Map<UUID, Set<UUID>> hiddenPerPlayer = new ConcurrentHashMap<>();

    public EntityVisibilityTask(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void run() {
        if (!config.isHideEntitiesEnabled()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!config.isWorldEnabled(player.getWorld().getName())) {
                continue;
            }

            Integer playerSectionY = Main.PLAYER_SECTION_Y.get(player.getUniqueId());
            if (playerSectionY == null) {
                continue;
            }

            int cutoffBlockY = config.computeCutoffSection(playerSectionY) << 4;
            processPlayer(player, cutoffBlockY);
        }
    }

    private void processPlayer(Player player, int cutoffBlockY) {
        Set<UUID> hidden = hiddenPerPlayer.computeIfAbsent(
                player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

        double radius = config.getEntityScanRadius();
        BoundingBox box = BoundingBox.of(player.getLocation(), radius, radius, radius);

        for (Entity entity : player.getWorld().getNearbyEntities(box)) {
            if (entity.equals(player)) {
                continue;
            }
            if (entity instanceof Player) {
                if (!config.isHidePlayersToo()) {
                    continue;
                }
            } else if (!(entity instanceof LivingEntity)) {
                continue;
            }

            boolean shouldBeHidden = entity.getLocation().getBlockY() < cutoffBlockY;
            UUID entityId = entity.getUniqueId();

            if (shouldBeHidden && hidden.add(entityId)) {
                player.hideEntity(plugin, entity);
            } else if (!shouldBeHidden && hidden.remove(entityId)) {
                player.showEntity(plugin, entity);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiddenPerPlayer.remove(event.getPlayer().getUniqueId());
    }
}
