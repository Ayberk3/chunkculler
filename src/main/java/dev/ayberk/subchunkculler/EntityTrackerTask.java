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
        if (viewer == null || !viewer.isOnline() || viewer.getWorld() == null) {
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

        // Bounded search volume: 64 blocks horizontal, 96 vertical (optimal for Paper entity tracking)
        Collection<Entity> nearbyEntities = viewer.getWorld().getNearbyEntities(
                viewerLoc, 64.0, 96.0, 64.0
        );

        for (Entity target : nearbyEntities) {
            if (target == null || !target.isValid() || target.getEntityId() == viewer.getEntityId()) {
                continue;
            }

            // In-flight projectiles are never hidden
            if (target instanceof Projectile) {
                continue;
            }

            double targetY = target.getLocation().getY();

            // If entity is below the culled subchunk cutoff -> HIDE recursively!
            if (targetY < cutoffBlockY) {
                hideEntityRecursively(viewer, target);
            } else {
                showEntityRecursively(viewer, target);
            }
        }
    }

    private void hideEntityRecursively(Player viewer, Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }

        if (viewer.canSee(entity)) {
            viewer.hideEntity(plugin, entity);
        }

        for (Entity passenger : entity.getPassengers()) {
            hideEntityRecursively(viewer, passenger);
        }

        if (entity.isInsideVehicle()) {
            Entity vehicle = entity.getVehicle();
            if (vehicle != null && viewer.canSee(vehicle)) {
                viewer.hideEntity(plugin, vehicle);
            }
        }
    }

    private void showEntityRecursively(Player viewer, Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }

        if (!viewer.canSee(entity)) {
            viewer.showEntity(plugin, entity);
        }

        for (Entity passenger : entity.getPassengers()) {
            showEntityRecursively(viewer, passenger);
        }

        if (entity.isInsideVehicle()) {
            Entity vehicle = entity.getVehicle();
            if (vehicle != null && !viewer.canSee(vehicle)) {
                viewer.showEntity(plugin, vehicle);
            }
        }
    }
}
