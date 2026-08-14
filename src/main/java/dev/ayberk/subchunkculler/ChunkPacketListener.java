package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.logging.Logger;

public final class ChunkPacketListener extends PacketListenerAbstract {

    private final ConfigManager config;
    private final Logger logger;

    private static final Field TILE_ENTITIES_FIELD = resolveTileEntitiesField();

    private static Field resolveTileEntitiesField() {
        try {
            Field f = Column.class.getDeclaredField("tileEntities");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

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
            return;
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

        if (strippedCount > 0) {
            stripTileEntitiesBelow(column, cutoffSection << 4);
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

    private void stripTileEntitiesBelow(Column column, int cutoffBlockY) {
        if (TILE_ENTITIES_FIELD == null) {
            return;
        }
        TileEntity[] entities = column.getTileEntities();
        if (entities == null || entities.length == 0) {
            return;
        }

        int keepCount = 0;
        for (TileEntity te : entities) {
            if (te.getY() >= cutoffBlockY) {
                keepCount++;
            }
        }
        if (keepCount == entities.length) {
            return;
        }

        TileEntity[] filtered = new TileEntity[keepCount];
        int idx = 0;
        for (TileEntity te : entities) {
            if (te.getY() >= cutoffBlockY) {
                filtered[idx++] = te;
            }
        }

        try {
            TILE_ENTITIES_FIELD.set(column, filtered);
        } catch (IllegalAccessException e) {
            // reflection blocked - leave tile entities as-is
        }
    }
}
