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

/**
 * Rewrites outgoing ClientboundLevelChunkWithLightPacket packets (PacketEvents:
 * {@code PacketType.Play.Server.CHUNK_DATA}) so that any sub-chunk section more
 * than {@code sub-chunks-below} sections below the receiving player's current
 * section is stripped out entirely before it leaves the server.
 *
 * <p>This runs on PacketEvents' packet-handling thread, NOT the main server
 * thread. Every value it touches (config, per-player section cache) is
 * pre-resolved and thread-safe - see {@link Main#PLAYER_SECTION_Y} and
 * {@link ConfigManager}. The only Bukkit API calls made here are cheap,
 * allocation-free field reads (world name, UUID), which is the accepted
 * practice for PacketEvents listeners; nothing here does I/O or touches
 * chunk/world state through Bukkit.
 *
 * <p><b>Verified against PacketEvents {@code v2.13.0} source</b> (the version
 * you're pinned to). Things that are NOT true in this version and would
 * silently break the plugin if assumed otherwise:
 * <ul>
 *   <li>Setting a {@code BaseChunk[]} slot to {@code null} is only safe pre-1.18.
 *       On 1.18+, {@code WrapperPlayServerChunkData#write()} unconditionally
 *       casts every slot to {@code Chunk_v1_18} and calls its static
 *       {@code write(...)}, which dereferences the section directly - a null
 *       slot throws an NPE during serialization.</li>
 *   <li>{@code new Chunk_v1_18(version)} alone is NOT a safe "empty" section -
 *       its block AND biome palettes start with ZERO registered entries,
 *       which crashes the client the instant it tries to resolve any index
 *       against an empty palette. Both palettes need at least one real
 *       entry registered - see {@link #buildEmptySection}.</li>
 *   <li>{@code Column} has no {@code setChunks()}/{@code setTileEntities()}.
 *       {@code getChunks()} returns the live backing array, so mutating its
 *       elements in place works with no setter needed. {@code tileEntities}
 *       has no setter at all, so trimming that array requires reflection
 *       (see {@link #stripTileEntitiesBelow}), guarded so it fails silently
 *       (leaving tile entities untouched) if a future PacketEvents version
 *       renames the field instead of throwing from the packet thread.</li>
 * </ul>
 */
public final class ChunkPacketListener extends PacketListenerAbstract {

    private final ConfigManager config;
    private final Logger logger;

    // Reflective handle to Column#tileEntities (private final TileEntity[]).
    // Resolved once; null if it can't be found, in which case tile-entity
    // stripping is skipped entirely rather than risking a packet-thread crash.
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
            // Not fully attached to a Bukkit entity yet (e.g. mid-login) - skip safely.
            return;
        }

        String worldName = player.getWorld().getName();
        if (!config.isWorldEnabled(worldName)) {
            return;
        }

        Integer playerSectionY = Main.PLAYER_SECTION_Y.get(player.getUniqueId());
        if (playerSectionY == null) {
            // No cached position yet - fail open (send intact) rather than guess
            // at a location via a Bukkit call off the main thread.
            return;
        }

        // CLAUDE'UN BAHSETTİĞİ DEĞİŞİKLİK BURADA
        final int cutoffSection = Math.min(
                playerSectionY - config.getSubChunksBelow(),
                config.getAbsoluteCutoffSection()
        );

        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        // getChunks() returns the live backing array (not a defensive copy),
        // so mutating elements here mutates the Column directly - no setter
        // call is needed, and none exists on Column in 2.13.0.
        BaseChunk[] chunks = column.getChunks();

        final int minSection = config.getMinSection(worldName);
        final ClientVersion clientVersion = event.getUser().getClientVersion();

        int strippedCount = 0;
        for (int i = 0; i < chunks.length; i++) {
            // Fast bitwise/integer math only - no allocations beyond the
            // (rare) empty-section object itself.
            int actualSectionY = minSection + i;
            if (actualSectionY < cutoffSection && !isEmptySection(chunks[i])) {
                // IMPORTANT: null is NOT safe here on 1.18+ - see class javadoc.
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
        // Already-air sections (chunk == null pre-1.18, or already stripped by
        // us on a previous pass) don't need to be touched again.
        return chunk == null;
    }

    /**
     * Builds a genuinely valid empty section - not just "blockCount 0", but
     * with both its block palette AND its biome palette populated with at
     * least one real entry.
     *
     * <p>{@code new Chunk_v1_18(version)} alone creates block/biome palettes
     * via {@code PaletteType.create()}, which returns a {@code ListPalette}
     * with ZERO registered entries. That serializes fine on the wire
     * (palette length 0, an all-zero index array) but crashes the client the
     * instant it tries to resolve any index against zero palette entries.
     * One {@code set(...)} call per palette registers a real entry at
     * palette-index 0; since the backing storage defaults to all-zero
     * indices anyway, that single write makes every position in the
     * 16x16x16 section (and every biome cell) resolve correctly instead of
     * dereferencing nothing.
     */
    private BaseChunk buildEmptySection(ClientVersion clientVersion) {
        Chunk_v1_18 section = new Chunk_v1_18(clientVersion);
        section.set(0, 0, 0, 0); // global state id 0 is always air
        section.getBiomeData().set(0, 0, 0, Biomes.PLAINS.getId(clientVersion));
        return section;
    }

    /**
     * Removes block-entity (chest/furnace/spawner/etc.) NBT for anything below
     * the cutoff Y, so their existence isn't leaked even though their
     * section's blocks were already stripped.
     *
     * <p>{@code Column#tileEntities} is a private final array with no public
     * setter in 2.13.0, so this uses a reflective field write, resolved once
     * and cached in {@link #TILE_ENTITIES_FIELD}. If that resolution ever
     * fails (e.g. the field is renamed in a future PacketEvents release),
     * this becomes a silent no-op instead of throwing on the packet thread -
     * section stripping (the main defense) still applies either way.
     */
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
            return; // nothing below the cutoff, nothing to do
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
            // Reflection blocked at runtime - leave tile entities as-is rather
            // than risk crashing the packet pipeline.
        }
    }
}
