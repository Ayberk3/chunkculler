package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public final class ChunkPacketListener extends PacketListenerAbstract {

    private static final TileEntity[] EMPTY_TILE_ENTITIES = new TileEntity[0];
    private final ConfigManager config;
    private final Logger logger;
    private int cachedFloorBlockId = -1;
    private String cachedFloorBlockName = "";

    public ChunkPacketListener(ConfigManager config) {
        super(PacketListenerPriority.NORMAL);
        this.config = config;
        this.logger = Logger.getLogger("SubChunkCuller");
    }

    private int getFloorBlockId() {
        String configured = config.getFakeFloorBlock();
        if (cachedFloorBlockId != -1 && configured.equals(cachedFloorBlockName)) {
            return cachedFloorBlockId;
        }

        cachedFloorBlockName = configured;
        try {
            Material material = Material.matchMaterial(configured);
            if (material == null) {
                material = Material.DEEPSLATE;
            }
            cachedFloorBlockId = SpigotConversionUtil.fromBukkitBlockData(material.createBlockData()).getGlobalId();
        } catch (Throwable t) {
            try {
                cachedFloorBlockId = SpigotConversionUtil.fromBukkitBlockData(Material.DEEPSLATE.createBlockData()).getGlobalId();
            } catch (Throwable ignored) {
                cachedFloorBlockId = 1;
            }
        }
        return cachedFloorBlockId;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) {
            return;
        }

        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player) || !player.isOnline()) {
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
        if (column == null) {
            return;
        }

        BaseChunk[] chunks = column.getChunks();
        if (chunks == null || chunks.length == 0) {
            return;
        }

        final int minSection = config.getMinSection(worldName);
        final ClientVersion clientVersion = event.getUser().getClientVersion();

        boolean fakeFloorEnabled = config.isFakeFloorEnabled();
        int fakeFloorSectionY = cutoffSection - 1;
        int floorBlockId = fakeFloorEnabled ? getFloorBlockId() : 0;

        int strippedCount = 0;
        for (int i = 0; i < chunks.length; i++) {
            int actualSectionY = minSection + i;
            if (actualSectionY >= cutoffSection) {
                continue;
            }

            if (fakeFloorEnabled && actualSectionY == fakeFloorSectionY) {
                chunks[i] = buildFakeFloorSection(floorBlockId, clientVersion);
                strippedCount++;
            } else {
                if (!isEmptySection(chunks[i])) {
                    chunks[i] = buildEmptySection(clientVersion);
                    strippedCount++;
                }
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
        if (source == null || source.length == 0) {
            return 0;
        }

        TileEntity[] kept = null;
        int keptCount = 0;
        int removed = 0;
        for (int index = 0; index < source.length; index++) {
            TileEntity tileEntity = source[index];
            if (tileEntity == null) {
                continue;
            }
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

    private BaseChunk buildFakeFloorSection(int blockStateId, ClientVersion clientVersion) {
        Chunk_v1_18 section = new Chunk_v1_18();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                section.set(x, 15, z, blockStateId);
            }
        }
        section.getBiomeData().set(0, 0, 0, Biomes.PLAINS.getId(clientVersion));
        return section;
    }
}
