package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.bukkit.entity.Player;

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

        final var type = event.getPacketType();
        if (type != PacketType.Play.Server.BLOCK_CHANGE
                && type != PacketType.Play.Server.MULTI_BLOCK_CHANGE
                && type != PacketType.Play.Server.BLOCK_ENTITY_DATA
                && type != PacketType.Play.Server.BLOCK_ACTION
                && type != PacketType.Play.Server.BLOCK_BREAK_ANIMATION
                && type != PacketType.Play.Server.EFFECT
                && type != PacketType.Play.Server.WORLD_EVENT
                && type != PacketType.Play.Server.PARTICLE) {
            return;
        }

        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player)) {
            return;
        }

        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        Integer viewerSectionY = Main.VIEWER_SECTION_Y.get(player.getUniqueId());
        if (viewerSectionY == null) {
            viewerSectionY = config.getAbsoluteCutoffSection();
        }

        final int cutoffSection = config.computeCutoffSection(viewerSectionY);
        final int cutoffBlockY = cutoffSection << 4;

        try {
            if (type == PacketType.Play.Server.BLOCK_CHANGE) {
                cancelIfBelow(event, new WrapperPlayServerBlockChange(event).getBlockPosition(), cutoffBlockY);
            } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                Vector3i sectionPos = new WrapperPlayServerMultiBlockChange(event).getChunkPosition();
                if (sectionPos != null && sectionPos.getY() < cutoffSection) {
                    event.setCancelled(true);
                }
            } else if (type == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
                cancelIfBelow(event, new WrapperPlayServerBlockEntityData(event).getPosition(), cutoffBlockY);
            } else if (type == PacketType.Play.Server.BLOCK_ACTION) {
                cancelIfBelow(event, new WrapperPlayServerBlockAction(event).getBlockPosition(), cutoffBlockY);
            } else if (type == PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
                cancelIfBelow(event, new WrapperPlayServerBlockBreakAnimation(event).getBlockPosition(), cutoffBlockY);
            } else if (type == PacketType.Play.Server.WORLD_EVENT || type == PacketType.Play.Server.EFFECT) {
                cancelIfBelow(event, new WrapperPlayServerWorldEvent(event).getPosition(), cutoffBlockY);
            } else if (type == PacketType.Play.Server.PARTICLE) {
                Vector3d pos = new WrapperPlayServerParticle(event).getPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            }
        } catch (Throwable ignored) {
            // Failsafe
        }
    }

    private void cancelIfBelow(PacketSendEvent event, Vector3i pos, int cutoffBlockY) {
        if (pos != null && pos.getY() < cutoffBlockY) {
            event.setCancelled(true);
        }
    }
}
