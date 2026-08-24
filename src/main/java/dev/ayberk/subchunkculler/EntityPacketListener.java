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
        } else if (packetType == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate infoUpdate = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate(event);
            if (infoUpdate.getActions().contains(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED)) {
                for (com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo info : infoUpdate.getEntries()) {
                    if (!info.isListed() && isCulledOnlinePlayer(player, info.getProfileId(), config)) {
                        // If it's a batch packet, canceling it outright might cancel other valid updates,
                        // but Bukkit rarely batches different players' listed updates.
                        event.setCancelled(true);
                        break;
                    }
                }
            }
            return;
        }

        if (player.hasPermission("subchunkculler.bypass")) {
            return;
        }

        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        // 2. Entity Spawn Blocker
        if (packetType == PacketType.Play.Server.SPAWN_ENTITY ||
            packetType == PacketType.Play.Server.SPAWN_PLAYER ||
            packetType == PacketType.Play.Server.SPAWN_ENTITY_LIVING ||
            packetType == PacketType.Play.Server.SPAWN_ENTITY_EXPERIENCE_ORB ||
            packetType == PacketType.Play.Server.SPAWN_ENTITY_PAINTING) {
            
            UUID viewerUUID = player.getUniqueId();
            Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(viewerUUID);
            if (viewerSectionY == null) {
                viewerSectionY = player.getLocation().getBlockY() >> 4;
            }
            int cutoffBlockY = config.computeCutoffSection(viewerSectionY) << 4;
            
            double entityY = 0;
            // WrapperPlayServerSpawnEntity in modern PacketEvents abstracts most of these, but we safely get the Y.
            // Some legacy wrappers might be needed if they are split, but modern WrapperPlayServerSpawnEntity works for all these types if mapped correctly.
            // If they are mapped as specific wrappers in very old versions, this could throw an exception, so we wrap it:
            try {
                if (packetType == PacketType.Play.Server.SPAWN_PLAYER) {
                    entityY = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer(event).getPosition().getY();
                } else if (packetType == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
                    entityY = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity(event).getPosition().getY();
                } else if (packetType == PacketType.Play.Server.SPAWN_ENTITY_EXPERIENCE_ORB) {
                    entityY = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnExperienceOrb(event).getPosition().getY();
                } else if (packetType == PacketType.Play.Server.SPAWN_ENTITY_PAINTING) {
                    entityY = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPainting(event).getPosition().getY();
                } else {
                    entityY = new WrapperPlayServerSpawnEntity(event).getPosition().getY();
                }
            } catch (Exception e) {
                // Fallback if wrapper parsing fails for a weird version
                return;
            }

            if (entityY < cutoffBlockY) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Checks if the target UUID belongs to an online player that was explicitly hidden *by us*.
     */
    private boolean isCulledOnlinePlayer(Player viewer, UUID targetUUID, ConfigManager config) {
        return Main.isPlayerCulledByUs(viewer.getUniqueId(), targetUUID);
    }
}
