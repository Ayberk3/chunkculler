package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import org.bukkit.entity.Player;

/**
 * ChunkPacketListener only sanitises CHUNK_DATA - the snapshot of a chunk at
 * the moment it is first sent to a viewer. But the server keeps talking about
 * that chunk afterwards: every block that changes later goes out as its own
 * BLOCK_CHANGE / MULTI_BLOCK_CHANGE packet, and those were never checked
 * against the cutoff.
 *
 * The practical result is that anything which updates below the cutoff gets
 * re-built on the client one block at a time, inside what is supposed to be
 * empty space. Flowing water is the most visible case - a freecam user sees
 * blue veins hanging in the void where the terrain was stripped - but the
 * same hole leaks pistons, doors, crops, redstone and block breaking too.
 *
 * This listener drops every block/effect packet aimed below the viewer's
 * cutoff. It is completely thread-safe: the only shared state it touches is
 * the ConcurrentHashMap in Main, and it never reads the world or a chunk.
 */
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

        final Object type = event.getPacketType();
        if (type != PacketType.Play.Server.BLOCK_CHANGE
                && type != PacketType.Play.Server.MULTI_BLOCK_CHANGE
                && type != PacketType.Play.Server.BLOCK_ENTITY_DATA
                && type != PacketType.Play.Server.BLOCK_ACTION
                && type != PacketType.Play.Server.BLOCK_BREAK_ANIMATION
                && type != PacketType.Play.Server.EFFECT
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
            // Same fail-closed policy as ChunkPacketListener: if we somehow
            // don't know where the viewer is, assume the most restrictive
            // cutoff rather than letting the update through.
            viewerSectionY = config.getAbsoluteCutoffSection();
        }

        final int cutoffSection = config.computeCutoffSection(viewerSectionY);
        final int cutoffBlockY = cutoffSection << 4;

        try {
            if (type == PacketType.Play.Server.BLOCK_CHANGE) {
                cancelIfBelow(event, new WrapperPlayServerBlockChange(event).getBlockPosition(), cutoffBlockY);

            } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                // This packet always describes a single 16x16x16 section, and
                // the cutoff is section-aligned, so comparing section Y is an
                // exact test - no need to look at the individual blocks.
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

            } else if (type == PacketType.Play.Server.EFFECT) {
                cancelIfBelow(event, new WrapperPlayServerEffect(event).getPosition(), cutoffBlockY);

            } else if (type == PacketType.Play.Server.PARTICLE) {
                Vector3d pos = new WrapperPlayServerParticle(event).getPosition();
                if (pos != null && pos.getY() < cutoffBlockY) {
                    event.setCancelled(true);
                }
            }
        } catch (Throwable ignored) {
            // A wrapper mismatch on some protocol version must never break the
            // packet pipeline - worst case that single packet goes unfiltered.
        }
    }

    private void cancelIfBelow(PacketSendEvent event, Vector3i pos, int cutoffBlockY) {
        if (pos != null && pos.getY() < cutoffBlockY) {
            event.setCancelled(true);
        }
    }
}

