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

    private volatile int subChunksBelow;
    private volatile boolean debugMode;
    private volatile int refreshRadius;
    private volatile long refreshCooldownMs;
    private volatile int refreshDebounceTicks;
    private volatile int refreshChunksPerTick;
    private volatile int absoluteCutoffSection;
    private volatile Set<String> enabledWorlds = Collections.emptySet();
    private volatile boolean fakeFloorEnabled;
    private volatile String fakeFloorBlock;
    private volatile boolean blockUpdateFilterEnabled;

    private final Map<String, Integer> worldMinSection = new ConcurrentHashMap<>();

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        this.subChunksBelow = Math.max(0, cfg.getInt("sub-chunks-below", 2));
        this.debugMode = cfg.getBoolean("debug-mode", false);
        this.refreshRadius = Math.max(1, cfg.getInt("refresh-radius", 3));
        this.refreshCooldownMs = Math.max(0L, cfg.getLong("refresh-cooldown-ms", 250L));
        this.refreshDebounceTicks = Math.max(0, cfg.getInt("refresh-debounce-ticks", 6));
        this.refreshChunksPerTick = Math.max(1, cfg.getInt("refresh-chunks-per-tick", 6));
        this.absoluteCutoffSection = cfg.getInt("absolute-cutoff-y", 0) >> 4;
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

    public int computeCutoffSection(int playerSectionY) {
        return Math.min(playerSectionY - subChunksBelow, absoluteCutoffSection);
    }

    public int getAbsoluteCutoffSection() {
        return absoluteCutoffSection;
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
}
