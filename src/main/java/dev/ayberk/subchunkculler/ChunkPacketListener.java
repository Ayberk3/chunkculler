package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * The plugin's ONLY job: strip chunk sections below the configured cutoff
 * from outgoing chunk packets. Nothing else - no tile-entity handling, no
 * entity/player visibility, no LOS. Just terrain Y-cutoff.
 */
public final class ChunkPacketListener extends PacketListenerAbstract {

    private final ConfigManager config;
    private final Logger logger;

    public ChunkPacketListener(ConfigManager config) {
        super(PacketListenerPriority.NORMAL);
        this.config = config;
        this.logger = Logger.getLogger("SubChunkCuller");
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) {
            return;
        }

        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player)) {
            return;
        }

        String worldName = player.getWorld().getName();
        if (!config.isWorldEnabled(worldName)) {
            return;
        }

        Integer playerSectionY = Main.VIEWER_SECTION_Y.get(player.getUniqueId());
        if (playerSectionY == null) {
            // Should not normally happen - ChunkRefreshListener seeds this on
            // PlayerLoginEvent, before any chunk packets can go out. If it's
            // still missing here, fail CLOSED (strip everything) instead of
            // silently letting an unprotected packet through.
            playerSectionY = config.getAbsoluteCutoffSection();
        }

        final int cutoffSection = config.computeCutoffSection(playerSectionY);

        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        BaseChunk[] chunks = column.getChunks();

        final int minSection = config.getMinSection(worldName);
        final ClientVersion clientVersion = event.getUser().getClientVersion();

        int strippedCount = 0;
        for (int i = 0; i < chunks.length; i++) {
            int actualSectionY = minSection + i;
            if (actualSectionY < cutoffSection && !isEmptySection(chunks[i])) {
                chunks[i] = buildEmptySection(clientVersion);
                strippedCount++;
            }
        }

        if (config.isDebugMode() && strippedCount > 0) {
            logger.info(String.format(
                    "[%s] chunk (%d,%d): stripped %d/%d section(s) below y=%d (player section %d, cutoff %d)",
                    player.getName(), column.getX(), column.getZ(), strippedCount, chunks.length,
                    cutoffSection << 4, playerSectionY, cutoffSection));
        }
    }

    private boolean isEmptySection(BaseChunk chunk) {
        return chunk == null;
    }

    private BaseChunk buildEmptySection(ClientVersion clientVersion) {
        Chunk_v1_18 section = new Chunk_v1_18(clientVersion);
        section.set(0, 0, 0, 0);
        section.getBiomeData().set(0, 0, 0, Biomes.PLAINS.getId(clientVersion));
        return section;
    }
}
