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

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePlayer(viewer);
        }
    }

    public void updatePlayer(Player viewer) {
        if (viewer == null || !viewer.isOnline() || viewer.hasPermission("subchunkculler.bypass")) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        if (!config.isWorldEnabled(viewer.getWorld().getName())) {
            return;
        }

        EntityTrackingManager trackingManager = plugin.getEntityTrackingManager();
        Location viewerLoc = viewer.getLocation();
        Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(viewer.getUniqueId());
        if (viewerSectionY == null) {
            viewerSectionY = viewerLoc.getBlockY() >> 4;
        }

        int cutoffSection = config.computeCutoffSection(viewerSectionY);
        int cutoffBlockY = cutoffSection << 4;

        Collection<Entity> nearbyEntities = viewer.getWorld().getNearbyEntities(
                viewerLoc, 96.0, 128.0, 96.0, trackingManager::isTargetApplicable
        );

        for (Entity target : nearbyEntities) {
            if (target.getEntityId() == viewer.getEntityId()) {
                continue;
            }

            double targetY = target.getLocation().getY();

            // If entity is below the culled subchunk cutoff -> HIDE immediately!
            if (targetY < cutoffBlockY) {
                trackingManager.hideEntity(viewer, target);
            } else {
                trackingManager.showEntity(viewer, target);
            }
        }
    }
}
