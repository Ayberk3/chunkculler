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
import java.util.logging.Logger;

/**
 * Blocks below the cutoff are already stripped from chunk packets, but
 * entities (mobs, and optionally other players) are tracked and sent to
 * the client through a completely separate packet path - so a mob-ESP /
 * entity-radar cheat can still see what's underground unless we hide the
 * entities themselves too.
 *
 * <p>This runs on the main thread on a repeating timer (NOT every tick -
 * scanning nearby entities for every online player every tick would be
 * wasteful for something that only needs to react within a fraction of a
 * second). It reuses {@link Main#PLAYER_SECTION_Y} and
 * {@link ConfigManager#computeCutoffSection(int)} - the exact same cutoff
 * math {@code ChunkPacketListener} uses - so terrain and entities are
 * always hidden/shown at the same line.
 *
 * <p>Uses the standard {@code Player#hideEntity(Plugin, Entity)} /
 * {@code showEntity(Plugin, Entity)} API rather than packet manipulation -
 * simpler and far less fragile than hand-rolling spawn/destroy packets,
 * at the cost of only reacting once every {@code entity-check-interval-ticks}
 * instead of instantly.
 *
 * <p><b>Known simplification:</b> if a hidden entity leaves a player's scan
 * radius entirely before crossing back above the cutoff, this task loses
 * track of it (no {@code showEntity} call is made for entities outside the
 * current scan). In practice this is harmless - Bukkit/Paper's own entity
 * tracker un-tracks entities that leave view distance and re-syncs
 * visibility when they're tracked again - but it's worth knowing about if
 * you ever see a mob that stays invisible longer than expected.
 */
public final class EntityVisibilityTask extends BukkitRunnable implements Listener {

    private final Main plugin;
    private final ConfigManager config;
    private final Logger logger = Logger.getLogger("SubChunkCuller");

    // Player UUID -> set of entity UUIDs THIS PLUGIN has hidden from them.
    // Only entities we hid ourselves are ever un-hidden by us.
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

        int scannedPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!config.isWorldEnabled(player.getWorld().getName())) {
                continue;
            }

            Integer playerSectionY = Main.PLAYER_SECTION_Y.get(player.getUniqueId());
            if (playerSectionY == null) {
                continue;
            }

            scannedPlayers++;
            int cutoffBlockY = config.computeCutoffSection(playerSectionY) << 4;
            processPlayer(player, cutoffBlockY);
        }

        if (config.isDebugMode()) {
            logger.info("[EntityVisibilityTask] tick ran, scanned " + scannedPlayers + " player(s)");
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
                // Skip item drops, XP orbs, arrows, etc. - only mobs/players
                // are what an ESP-style radar actually cares about, and
                // hiding non-living entities too would multiply the work
                // here for no real anti-cheat benefit.
                continue;
            }

            boolean shouldBeHidden = entity.getLocation().getBlockY() < cutoffBlockY;
            UUID entityId = entity.getUniqueId();

            if (shouldBeHidden && hidden.add(entityId)) {
                player.hideEntity(plugin, entity);
                if (config.isDebugMode()) {
                    logger.info("[EntityVisibilityTask] hid " + entity.getType()
                            + " at y=" + entity.getLocation().getBlockY()
                            + " from " + player.getName() + " (cutoff y=" + cutoffBlockY + ")");
                }
            } else if (!shouldBeHidden && hidden.remove(entityId)) {
                player.showEntity(plugin, entity);
                if (config.isDebugMode()) {
                    logger.info("[EntityVisibilityTask] showed " + entity.getType()
                            + " to " + player.getName());
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiddenPerPlayer.remove(event.getPlayer().getUniqueId());
    }
}
