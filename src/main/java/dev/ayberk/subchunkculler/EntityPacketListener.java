package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EntityPacketListener extends PacketListenerAbstract {

    private final Main plugin;

    public EntityPacketListener(Main plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!plugin.getConfigManager().isEntityCullerEnabled()) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        var packetType = event.getPacketType();
        ConfigManager config = plugin.getConfigManager();

        // 1. Tab List Protector
        // Bukkit's hideEntity() removes the player from the Tab list. We intercept the packet to keep them in Tab!
        if (packetType == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(event);
            for (UUID uuid : removePacket.getProfileIds()) {
                if (isCulledOnlinePlayer(player, uuid, config)) {
                    event.setCancelled(true);
                    break;
                }
            }
            return;
        } else if (packetType == PacketType.Play.Server.PLAYER_INFO) {
            WrapperPlayServerPlayerInfo infoPacket = new WrapperPlayServerPlayerInfo(event);
            if (infoPacket.getAction() == WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER) {
                for (WrapperPlayServerPlayerInfo.PlayerData data : infoPacket.getPlayerDataList()) {
                    if (isCulledOnlinePlayer(player, data.getUserProfile().getUUID(), config)) {
                        event.setCancelled(true);
                        break;
                    }
                }
            }
            return; // We only intercept Tab actions here
        }

        if (player.hasPermission("subchunkculler.bypass")) {
            return;
        }

        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        // 2. Entity Spawn Blocker
        if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
            UUID viewerUUID = player.getUniqueId();
            Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(viewerUUID);
            if (viewerSectionY == null) {
                viewerSectionY = player.getLocation().getBlockY() >> 4;
            }
            int cutoffBlockY = config.computeCutoffSection(viewerSectionY) << 4;
            
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            double entityY = packet.getPosition().getY();

            if (entityY < cutoffBlockY) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Checks if the target UUID belongs to an online player that was hidden *by us* (below cutoff).
     */
    private boolean isCulledOnlinePlayer(Player viewer, UUID targetUUID, ConfigManager config) {
        Player target = Bukkit.getPlayer(targetUUID);
        
        // If they are online and the viewer cannot see them (hideEntity was used)
        if (target != null && target.isOnline() && !viewer.canSee(target)) {
            
            // Confirm they are actually below our cutoff (so it was our plugin that hid them, not a vanish plugin)
            Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(viewer.getUniqueId());
            if (viewerSectionY == null) {
                viewerSectionY = viewer.getLocation().getBlockY() >> 4;
            }
            int cutoffBlockY = config.computeCutoffSection(viewerSectionY) << 4;
            
            if (target.getLocation().getY() < cutoffBlockY) {
                return true; // We protect them from being removed from the Tab list!
            }
        }
        return false;
    }
}
