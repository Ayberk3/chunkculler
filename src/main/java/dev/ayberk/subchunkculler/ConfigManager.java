package dev.ayberk.subchunkculler;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigManager implements Listener {

    private final Main plugin;

    // SubChunk Settings
    private volatile int subChunksBelow;
    private volatile int absoluteCutoffY;
    private volatile int absoluteCutoffSection;
    private volatile boolean debugMode;
    private volatile int refreshRadius;
    private volatile long refreshCooldownMs;
    private volatile int refreshDebounceTicks;
    private volatile int refreshChunksPerTick;
    private volatile Set<String> enabledWorlds = Collections.emptySet();
    private volatile boolean fakeFloorEnabled;
    private volatile String fakeFloorBlock;
    private volatile boolean blockUpdateFilterEnabled;

    // Entity Culler Settings
    private volatile boolean entityCullerEnabled;
    private volatile int checkIntervalTicks;
    private volatile boolean hidePlayers;
    private volatile boolean hideMonsters;
    private volatile boolean hideAnimals;
    private volatile boolean hideMisc;
    private volatile boolean bypassNpcs;
    private volatile boolean bypassHolograms;
    private volatile boolean dampenUndergroundSounds;

    private final Map<String, Integer> worldMinSection = new ConcurrentHashMap<>();

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        // 1. SubChunk
        this.subChunksBelow = Math.max(0, cfg.getInt("sub-chunks-below", 2));
        this.absoluteCutoffY = cfg.getInt("absolute-cutoff-y", 0);
        this.absoluteCutoffSection = this.absoluteCutoffY >> 4;
        this.debugMode = cfg.getBoolean("debug-mode", false);
        this.refreshRadius = Math.max(1, cfg.getInt("refresh-radius", 8));
        this.refreshCooldownMs = Math.max(0L, cfg.getLong("refresh-cooldown-ms", 250L));
        this.refreshDebounceTicks = Math.max(0, cfg.getInt("refresh-debounce-ticks", 4));
        this.refreshChunksPerTick = Math.max(1, cfg.getInt("refresh-chunks-per-tick", 12));
        this.fakeFloorEnabled = cfg.getBoolean("fake-floor.enabled", true);
        String configuredBlock = cfg.getString("fake-floor.block", "minecraft:deepslate");
        this.fakeFloorBlock = (configuredBlock == null || configuredBlock.isBlank())
                ? "minecraft:deepslate" : configuredBlock.trim();
        this.blockUpdateFilterEnabled = cfg.getBoolean("block-update-filter.enabled", true);

        Set<String> worlds = new HashSet<>(cfg.getStringList("enabled-worlds"));
        if (worlds.isEmpty()) {
            worlds.add("world");
        }
        this.enabledWorlds = worlds;

        // 2. Entity Culler
        this.entityCullerEnabled = cfg.getBoolean("entity-culler.enabled", true);
        this.checkIntervalTicks = Math.max(1, cfg.getInt("entity-culler.check-interval-ticks", 4));
        this.hidePlayers = cfg.getBoolean("entity-culler.targets.hide-players", true);
        this.hideMonsters = cfg.getBoolean("entity-culler.targets.hide-monsters", true);
        this.hideAnimals = cfg.getBoolean("entity-culler.targets.hide-animals", false);
        this.hideMisc = cfg.getBoolean("entity-culler.targets.hide-misc", true);
        this.bypassNpcs = cfg.getBoolean("entity-culler.targets.bypass-npcs", true);
        this.bypassHolograms = cfg.getBoolean("entity-culler.targets.bypass-holograms", true);
        this.dampenUndergroundSounds = cfg.getBoolean("entity-culler.dampen-underground-sounds", true);

        worldMinSection.clear();
        for (World world : plugin.getServer().getWorlds()) {
            cacheWorld(world);
        }
    }

    public void reload() {
        load();
    }

    private void cacheWorld(World world) {
        worldMinSection.put(world.getName(), world.getMinHeight() >> 4);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        cacheWorld(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        worldMinSection.remove(event.getWorld().getName());
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains(worldName);
    }

    public int getMinSection(String worldName) {
        Integer cached = worldMinSection.get(worldName);
        return cached != null ? cached : -4;
    }

    public int getSubChunksBelow() {
        return subChunksBelow;
    }

    public int getAbsoluteCutoffY() {
        return absoluteCutoffY;
    }

    public int getAbsoluteCutoffSection() {
        return absoluteCutoffSection;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getRefreshRadius() {
        return refreshRadius;
    }

    public long getRefreshCooldownMs() {
        return refreshCooldownMs;
    }

    public int getRefreshDebounceTicks() {
        return refreshDebounceTicks;
    }

    public int getRefreshChunksPerTick() {
        return refreshChunksPerTick;
    }

    /**
     * Computes the vertical cutoff section.
     *
     * Logic:
     * - dynamicCutoff = playerSectionY - subChunksBelow
     *   (how far below the player we allow visibility)
     * - absoluteCutoffSection = absolute-cutoff-y >> 4
     *   (the absolute floor: never cull ABOVE this section)
     *
     * We take Math.max: whichever is HIGHER (less aggressive) wins.
     * - Surface player Y=80, section=5, dynamic=3, absolute=0 → max(3,0)=3 → cull below section 3 (Y=48). Good.
     * - Underground player Y=-20, section=-2, dynamic=-4, absolute=0 → max(-4,0)=0 → cull below section 0 (Y=0). Good.
     * - Deep underground player Y=-50, section=-4, dynamic=-6, absolute=0 → max(-6,0)=0 → cull below Y=0. Good.
     */
    public int computeCutoffSection(int playerSectionY) {
        int dynamicCutoff = playerSectionY - subChunksBelow;
        return Math.max(dynamicCutoff, absoluteCutoffSection);
    }

    public boolean isFakeFloorEnabled() {
        return fakeFloorEnabled;
    }

    public String getFakeFloorBlock() {
        return fakeFloorBlock;
    }

    public boolean isBlockUpdateFilterEnabled() {
        return blockUpdateFilterEnabled;
    }

    public boolean isEntityCullerEnabled() {
        return entityCullerEnabled;
    }

    public int getCheckIntervalTicks() {
        return checkIntervalTicks;
    }

    public boolean isHidePlayers() {
        return hidePlayers;
    }

    public boolean isHideMonsters() {
        return hideMonsters;
    }

    public boolean isHideAnimals() {
        return hideAnimals;
    }

    public boolean isHideMisc() {
        return hideMisc;
    }

    public boolean isBypassNpcs() {
        return bypassNpcs;
    }

    public boolean isBypassHolograms() {
        return bypassHolograms;
    }

    public boolean isDampenUndergroundSounds() {
        return dampenUndergroundSounds;
    }
}
