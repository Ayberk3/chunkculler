package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class BlockUpdateListener extends PacketListenerAbstract {

    private final ConfigManager config;

    public BlockUpdateListener(ConfigManager config) {
        super(PacketListenerPriority.NORMAL);
        this.config = config;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!config.isBlockUpdateFilterEnabled()) {
            return;
        }

        var packetType = event.getPacketType();
        if (packetType != PacketType.Play.Server.BLOCK_CHANGE
                && packetType != PacketType.Play.Server.MULTI_BLOCK_CHANGE
                && packetType != PacketType.Play.Server.BLOCK_ENTITY_DATA
                && packetType != PacketType.Play.Server.BLOCK_ACTION
                && packetType != PacketType.Play.Server.BLOCK_BREAK_ANIMATION
                && packetType != PacketType.Play.Server.PARTICLE
                && packetType != PacketType.Play.Server.EXPLOSION) {
            return;
        }

        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player) || !player.isOnline()) {
            return;
        }

        if (player.hasPermission("subchunkculler.bypass")) {
            return;
        }

        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        UUID viewerUUID = player.getUniqueId();
        Integer playerSectionY = Main.VIEWER_SECTION_Y.get(viewerUUID);
        if (playerSectionY == null) {
            playerSectionY = player.getLocation().getBlockY() >> 4;
            Main.VIEWER_SECTION_Y.put(viewerUUID, playerSectionY);
        }

        int cutoffSection = config.computeCutoffSection(playerSectionY);
        int cutoffBlockY = cutoffSection << 4;

        try {
            if (packetType == PacketType.Play.Server.BLOCK_CHANGE) {
                WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange(event);
                Vector3i pos = blockChange.getBlockPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            } else if (packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                WrapperPlayServerMultiBlockChange multi = new WrapperPlayServerMultiBlockChange(event);
                Vector3i chunkPos = multi.getChunkPosition();
                if (chunkPos != null && chunkPos.getY() < cutoffSection) {
                    event.setCancelled(true);
                }
            } else if (packetType == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
                WrapperPlayServerBlockEntityData bed = new WrapperPlayServerBlockEntityData(event);
                Vector3i pos = bed.getPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            } else if (packetType == PacketType.Play.Server.BLOCK_ACTION) {
                WrapperPlayServerBlockAction ba = new WrapperPlayServerBlockAction(event);
                Vector3i pos = ba.getBlockPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            } else if (packetType == PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
                WrapperPlayServerBlockBreakAnimation bba = new WrapperPlayServerBlockBreakAnimation(event);
                Vector3i pos = bba.getBlockPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            } else if (packetType == PacketType.Play.Server.PARTICLE) {
                WrapperPlayServerParticle particle = new WrapperPlayServerParticle(event);
                var pos = particle.getPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            } else if (packetType == PacketType.Play.Server.EXPLOSION) {
                WrapperPlayServerExplosion explosion = new WrapperPlayServerExplosion(event);
                var pos = explosion.getPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
