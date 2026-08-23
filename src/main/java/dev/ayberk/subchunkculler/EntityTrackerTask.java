package dev.ayberk.subchunkculler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public final class EntityTrackerTask extends BukkitRunnable {

    private final Main plugin;

    public EntityTrackerTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().isEntityCullerEnabled()) {
            return;
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePlayer(viewer);
        }
    }

    public void updatePlayer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        if (!config.isWorldEnabled(viewer.getWorld().getName())) {
            return;
        }

        Location viewerLoc = viewer.getLocation();
        Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(viewer.getUniqueId());
        if (viewerSectionY == null) {
            viewerSectionY = viewerLoc.getBlockY() >> 4;
        }

        int cutoffSection = config.computeCutoffSection(viewerSectionY);
        int cutoffBlockY = cutoffSection << 4;

        // Optimized radius: 64 blocks horizontally and 96 blocks vertically (matching Paper tracking range)
        Collection<Entity> nearbyEntities = viewer.getWorld().getNearbyEntities(
                viewerLoc, 64.0, 96.0, 64.0
        );

        for (Entity target : nearbyEntities) {
            if (target.getEntityId() == viewer.getEntityId()) {
                continue;
            }

            // Projectiles are never hidden
            if (target instanceof Projectile) {
                continue;
            }

            double targetY = target.getLocation().getY();

            // If entity is below the culled subchunk cutoff -> HIDE natively!
            if (targetY < cutoffBlockY) {
                if (viewer.canSee(target)) {
                    viewer.hideEntity(plugin, target);
                }
            } else {
                if (!viewer.canSee(target)) {
                    viewer.showEntity(plugin, target);
                }
            }
        }
    }
}
