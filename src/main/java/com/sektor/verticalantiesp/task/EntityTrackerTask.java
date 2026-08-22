package com.sektor.verticalantiesp.task;

import com.sektor.verticalantiesp.VerticalAntiESP;
import com.sektor.verticalantiesp.manager.EntityTrackingManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class EntityTrackerTask extends BukkitRunnable {

    private final VerticalAntiESP plugin;

    public EntityTrackerTask(VerticalAntiESP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        double hideDistanceY = plugin.getConfigManager().getHideDistanceY();
        double showDistanceY = plugin.getConfigManager().getShowDistanceY();
        EntityTrackingManager trackingManager = plugin.getTrackingManager();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.isOnline() || viewer.hasPermission("verticalantiesp.bypass")) {
                continue;
            }

            Location viewerLoc = viewer.getLocation();
            double viewerY = viewerLoc.getY();

            // Track entities within a 64x128x64 bounding box around player
            Collection<Entity> nearbyEntities = viewer.getWorld().getNearbyEntities(
                    viewerLoc, 64.0, 128.0, 64.0, trackingManager::isTargetApplicable
            );

            for (Entity target : nearbyEntities) {
                if (target.getEntityId() == viewer.getEntityId()) {
                    continue;
                }

                double targetY = target.getLocation().getY();
                double deltaY = viewerY - targetY;

                // 1. If target is deeper than or equal to hide distance -> HIDE
                if (deltaY >= hideDistanceY) {
                    trackingManager.hideEntity(viewer, target);
                }
                // 2. If target comes within show distance buffer -> SHOW
                else if (deltaY <= showDistanceY) {
                    trackingManager.showEntity(viewer, target);
                }
            }
        }
    }
}
