package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public final class ChunkPacketListener extends PacketListenerAbstract {

    private static final TileEntity[] EMPTY_TILE_ENTITIES = new TileEntity[0];
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
            playerSectionY = player.getLocation().getBlockY() >> 4;
            Main.VIEWER_SECTION_Y.put(player.getUniqueId(), playerSectionY);
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
            if (actualSectionY >= cutoffSection) {
                continue;
            }

            if (!isEmptySection(chunks[i])) {
                chunks[i] = buildEmptySection(clientVersion);
                strippedCount++;
            }
        }

        int strippedTileEntities = stripHiddenTileEntities(wrapper, column, cutoffSection << 4);

        if (config.isDebugMode() && (strippedCount > 0 || strippedTileEntities > 0)) {
            logger.info(String.format(
                    "[%s] chunk (%d,%d): stripped %d/%d section(s) and %d tile entit%s below y=%d (player section %d, cutoff %d)",
                    player.getName(), column.getX(), column.getZ(), strippedCount, chunks.length,
                    strippedTileEntities, strippedTileEntities == 1 ? "y" : "ies",
                    cutoffSection << 4, playerSectionY, cutoffSection));
        }
    }

    private int stripHiddenTileEntities(WrapperPlayServerChunkData wrapper, Column column, int cutoffBlockY) {
        TileEntity[] source = column.getTileEntities();
        if (source.length == 0) {
            return 0;
        }

        TileEntity[] kept = null;
        int keptCount = 0;
        int removed = 0;
        for (int index = 0; index < source.length; index++) {
            TileEntity tileEntity = source[index];
            if (tileEntity.getY() < cutoffBlockY) {
                removed++;
                if (kept == null) {
                    kept = new TileEntity[source.length - 1];
                    if (index > 0) {
                        System.arraycopy(source, 0, kept, 0, index);
                        keptCount = index;
                    }
                }
                continue;
            }
            if (kept != null) {
                kept[keptCount++] = tileEntity;
            }
        }

        if (removed == 0) {
            return 0;
        }

        TileEntity[] filtered = keptCount == 0
                ? EMPTY_TILE_ENTITIES
                : (keptCount == kept.length ? kept : java.util.Arrays.copyOf(kept, keptCount));

        Column rebuilt = new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), filtered, column.getHeightMaps());
        wrapper.setColumn(rebuilt);
        return removed;
    }

    private boolean isEmptySection(BaseChunk chunk) {
        return chunk == null;
    }

    private BaseChunk buildEmptySection(ClientVersion clientVersion) {
        Chunk_v1_18 section = new Chunk_v1_18();
        section.set(0, 0, 0, 0);
        section.getBiomeData().set(0, 0, 0, Biomes.PLAINS.getId(clientVersion));
        return section;
    }
}
