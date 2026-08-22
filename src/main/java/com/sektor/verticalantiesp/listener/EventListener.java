package com.sektor.verticalantiesp.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.sektor.verticalantiesp.VerticalAntiESP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class EventListener implements Listener {

    private final VerticalAntiESP plugin;

    public EventListener(VerticalAntiESP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getTrackingManager().removeViewer(player.getUniqueId());
        plugin.getTrackingManager().removeEntity(player.getEntityId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getTrackingManager().removeViewer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getTrackingManager().removeViewer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Reset viewer tracking cache upon major teleport to prevent visual artifacts
        Player player = event.getPlayer();
        if (event.getFrom().distanceSquared(event.getTo()) > 64) {
            plugin.getTrackingManager().removeViewer(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.getTrackingManager().removeEntity(event.getEntity().getEntityId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        plugin.getTrackingManager().removeEntity(event.getEntity().getEntityId());
    }
}
