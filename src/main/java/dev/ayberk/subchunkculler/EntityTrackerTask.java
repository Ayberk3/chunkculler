package dev.ayberk.subchunkculler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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

        double hideDistanceY = plugin.getConfigManager().getHideDistanceY();
        double showDistanceY = plugin.getConfigManager().getShowDistanceY();
        EntityTrackingManager trackingManager = plugin.getEntityTrackingManager();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.isOnline() || viewer.hasPermission("subchunkculler.bypass")) {
                continue;
            }

            Location viewerLoc = viewer.getLocation();
            double viewerY = viewerLoc.getY();

            Collection<Entity> nearbyEntities = viewer.getWorld().getNearbyEntities(
                    viewerLoc, 64.0, 128.0, 64.0, trackingManager::isTargetApplicable
            );

            for (Entity target : nearbyEntities) {
                if (target.getEntityId() == viewer.getEntityId()) {
                    continue;
                }

                double targetY = target.getLocation().getY();
                double deltaY = viewerY - targetY;

                if (deltaY >= hideDistanceY) {
                    trackingManager.hideEntity(viewer, target);
                } else if (deltaY <= showDistanceY) {
                    trackingManager.showEntity(viewer, target);
                }
            }
        }
    }
}
