package dev.ayberk.subchunkculler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The plugin's job: strip chunk sections below the configured cutoff from
 * outgoing chunk packets (plus an optional cosmetic floor layer right at the
 * boundary - see fake-floor.* in config.yml), AND drop the block-entity
 * (tile entity - chests, hoppers, furnaces, signs, spawners, ...) records
 * for anything that ends up inside a hidden/faked section.
 *
 * The block-entity list travels in the SAME chunk packet as the section
 * data, but as a completely separate array (Column#getTileEntities()) that
 * is NOT tied to what block is actually rendered at that position. Blanking
 * a section's blocks to air/deepslate does nothing to that list on its own -
 * the full NBT of every chest/hopper/etc. in the hidden area (including
 * container contents) would still reach the client. This is exactly what a
 * RaycastedAntiESP install on the same server was catching and stripping a
 * second time ("Received invalid or uncached chunk block entity" warnings) -
 * it should never have had anything to catch here in the first place.
 */
public final class ChunkPacketListener extends PacketListenerAbstract {

    private static final TileEntity[] EMPTY_TILE_ENTITIES = new TileEntity[0];

    private final ConfigManager config;
    private final Logger logger;
    private final boolean modernHeightmaps;

    // Resolved per-ClientVersion block state for the fake floor. Cleared
    // whenever the configured block string changes (e.g. via /reload).
    private final Map<ClientVersion, WrappedBlockState> floorStateCache = new ConcurrentHashMap<>();
    private volatile String cachedFloorBlockName = "";

    public ChunkPacketListener(ConfigManager config) {
        super(PacketListenerPriority.NORMAL);
        this.config = config;
        this.logger = Logger.getLogger("SubChunkCuller");
        this.modernHeightmaps = PacketEvents.getAPI().getServerManager()
                .getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_5);
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
        // The highest section that gets fully stripped - this is where the
        // fake floor (if enabled) gets painted, right at the boundary.
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

            if (drawFloor && actualSectionY == floorSectionY) {
                // Always paint the floor here, even if this section was
                // already empty (nothing generated, void, etc.) - if the
                // floor only showed up when real terrain was actually
                // hidden underneath, its presence/absence would itself
                // leak whether something is there, which defeats the
                // point of camouflaging the cutoff in the first place.
                chunks[i] = buildFloorSection(clientVersion);
                strippedCount++;
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

    /**
     * Removes every block-entity record at or below {@code cutoffBlockY}
     * from the packet's tile-entity list. Column#getTileEntities() is a
     * plain array with no setter, so unlike the section array (which we
     * mutate element-by-element in place above) a removal has to go through
     * building a new, shorter array and a new Column - mirrors exactly what
     * RaycastedAntiESP's own AbstractChunkParser#copyColumn does, so the
     * result stays compatible whether or not that plugin is installed.
     */
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
                    // First removal we've hit - everything before this index
                    // was kept, so seed the new array with it in one copy.
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

        Column rebuilt = modernHeightmaps
                ? new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), filtered, column.getHeightmaps())
                : new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), filtered, column.getHeightMaps());
        wrapper.setColumn(rebuilt);
        return removed;
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

    private BaseChunk buildFloorSection(ClientVersion clientVersion) {
        Chunk_v1_18 section = new Chunk_v1_18(clientVersion);
        WrappedBlockState floorState = resolveFloorState(clientVersion);
        // Top row of this section (local y=15) sits exactly one block
        // below the cutoff line - that's where the player's view of "solid
        // ground" should start.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                section.set(x, 15, z, floorState);
            }
        }
        section.getBiomeData().set(0, 0, 0, Biomes.PLAINS.getId(clientVersion));
        return section;
    }

    private WrappedBlockState resolveFloorState(ClientVersion clientVersion) {
        String configuredBlock = config.getFakeFloorBlock();
        if (!configuredBlock.equals(cachedFloorBlockName)) {
            floorStateCache.clear();
            cachedFloorBlockName = configuredBlock;
        }
        return floorStateCache.computeIfAbsent(clientVersion, cv -> resolveConfiguredOrDefault(cv, configuredBlock));
    }

    private WrappedBlockState resolveConfiguredOrDefault(ClientVersion clientVersion, String configuredBlock) {
        try {
            WrappedBlockState state = WrappedBlockState.getByString(clientVersion, configuredBlock);
            if (state != null) {
                return state;
            }
        } catch (Exception e) {
            // fall through to the default below
        }
        logger.warning("fake-floor.block '" + configuredBlock
                + "' could not be resolved - falling back to minecraft:deepslate");
        return WrappedBlockState.getDefaultState(clientVersion, StateTypes.DEEPSLATE);
    }
}
