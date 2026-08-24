package dev.ayberk.subchunkculler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

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

        // BUG-6 FIX: bypass permission check
        if (viewer.hasPermission("subchunkculler.bypass")) {
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

        // BUG-5 FIX: Use getWorld().getEntities() with manual distance check
        // instead of getNearbyEntities() which creates a new ArrayList+AABB every call.
        // We iterate the world entity list (cached by Paper, zero-alloc) and check distance manually.
        List<Entity> worldEntities = viewer.getWorld().getEntities();
        double viewerX = viewerLoc.getX();
        double viewerZ = viewerLoc.getZ();

        for (Entity target : worldEntities) {
            if (target == null || !target.isValid() || target.getEntityId() == viewer.getEntityId()) {
                continue;
            }

            // Skip projectiles
            if (target instanceof Projectile) {
                continue;
            }

            // BUG-7 FIX: Respect entity type filter config
            if (!isTargetApplicable(target, config)) {
                continue;
            }

            Location targetLoc = target.getLocation();

            // Manual distance check instead of AABB (64 block horizontal radius)
            double dx = targetLoc.getX() - viewerX;
            double dz = targetLoc.getZ() - viewerZ;
            if (dx * dx + dz * dz > 4096.0) { // 64^2
                continue;
            }

            double targetY = targetLoc.getY();

            if (targetY < cutoffBlockY) {
                hideEntityRecursively(viewer, target);
            } else {
                showEntityRecursively(viewer, target);
            }
        }
    }

    /**
     * BUG-7 FIX: Check entity type against config before hiding.
     */
    private boolean isTargetApplicable(Entity entity, ConfigManager config) {
        // NPC bypass (Citizens, FancyNpcs, etc.)
        if (config.isBypassNpcs()) {
            if (entity.hasMetadata("NPC") || entity.hasMetadata("shopkeeper")
                    || entity.getScoreboardTags().contains("fancynpcs:npc")) {
                return false;
            }
        }

        // Hologram bypass (DecentHolograms, HolographicDisplays, etc.)
        if (config.isBypassHolograms()) {
            if (entity instanceof ArmorStand stand) {
                if (stand.isMarker() || stand.getScoreboardTags().contains("dh_hologram")
                        || stand.hasMetadata("dh_hologram")) {
                    return false;
                }
            }
        }

        // Skip falling blocks, dropped items
        if (entity instanceof FallingBlock || entity instanceof Item) {
            return false;
        }

        if (entity instanceof Player) {
            return config.isHidePlayers();
        }
        if (entity instanceof Monster) {
            return config.isHideMonsters();
        }
        if (entity instanceof Animals || entity instanceof WaterMob) {
            return config.isHideAnimals();
        }
        return config.isHideMisc();
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
