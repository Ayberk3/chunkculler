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
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ChunkPacketListener extends PacketListenerAbstract {

    private static final TileEntity[] EMPTY_TILE_ENTITIES = new TileEntity[0];
    private final ConfigManager config;
    private final Logger logger;
    private final Map<ClientVersion, WrappedBlockState> floorStateCache = new ConcurrentHashMap<>();
    private String cachedFloorBlockName = "";

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
            playerSectionY = config.getAbsoluteCutoffSection();
        }

        final int cutoffSection = config.computeCutoffSection(playerSectionY);
        final int floorSectionY = cutoffSection - 1;
        final boolean drawFloor = config.isFakeFloorEnabled();

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

            // Fake Floor Section (Exactly 1 section below cutoff, top row y=15)
            if (drawFloor && actualSectionY == floorSectionY) {
                chunks[i] = applyFloorToSection(chunks[i], clientVersion);
                strippedCount++;
                continue;
            }

            // Fully stripped sections below fake floor
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
        int biomeId = Biomes.PLAINS.getId(clientVersion);
        for (int bx = 0; bx < 4; bx++) {
            for (int by = 0; by < 4; by++) {
                for (int bz = 0; bz < 4; bz++) {
                    section.getBiomeData().set(bx, by, bz, biomeId);
                }
            }
        }
        return section;
    }

    private BaseChunk applyFloorToSection(BaseChunk existing, ClientVersion clientVersion) {
        WrappedBlockState floorState = resolveFloorState(clientVersion);
        int stateId = floorState.getGlobalId();
        int biomeId = Biomes.PLAINS.getId(clientVersion);

        BaseChunk section = existing != null ? existing : new Chunk_v1_18();

        // 1. Clear all blocks below y=15 and place floor block at y=15
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 15; y++) {
                    section.set(x, y, z, 0); // Air
                }
                section.set(x, 15, z, stateId); // Deepslate / Configured block
            }
        }

        // 2. Ensure biome palette is fully populated (prevent client rendering crash)
        for (int bx = 0; bx < 4; bx++) {
            for (int by = 0; by < 4; by++) {
                for (int bz = 0; bz < 4; bz++) {
                    section.getBiomeData().set(bx, by, bz, biomeId);
                }
            }
        }

        return section;
    }

    private WrappedBlockState resolveFloorState(ClientVersion clientVersion) {
        String configuredBlock = config.getFakeFloorBlock();
        if (!configuredBlock.equals(cachedFloorBlockName)) {
            floorStateCache.clear();
            cachedFloorBlockName = configuredBlock;
        }
        return floorStateCache.computeIfAbsent(clientVersion, cv -> {
            try {
                WrappedBlockState state = WrappedBlockState.getByString(cv, configuredBlock);
                if (state != null) {
                    return state;
                }
            } catch (Exception ignored) {
            }
            try {
                WrappedBlockState fallback = WrappedBlockState.getByString(cv, "minecraft:deepslate");
                if (fallback != null) {
                    return fallback;
                }
            } catch (Exception ignored) {
            }
            return WrappedBlockState.getDefaultState(cv, StateTypes.DEEPSLATE);
        });
    }
}
