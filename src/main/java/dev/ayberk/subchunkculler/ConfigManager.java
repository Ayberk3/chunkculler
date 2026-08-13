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
    private volatile int absoluteCutoffSection;
    private volatile Set<String> enabledWorlds = Collections.emptySet();

    private volatile boolean losEnabled;
    private volatile double losMaxDistance;
    private volatile int losCheckIntervalTicks;

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
        this.absoluteCutoffSection = cfg.getInt("absolute-cutoff-y", 0) >> 4;

        this.losEnabled = cfg.getBoolean("line-of-sight.enabled", true);
        this.losMaxDistance = Math.max(1.0, Math.min(128.0, cfg.getDouble("line-of-sight.max-distance", 48.0)));
        this.losCheckIntervalTicks = Math.max(5, cfg.getInt("line-of-sight.check-interval-ticks", 20));

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

    public int computeCutoffSection(int playerSectionY) {
        return Math.min(playerSectionY - subChunksBelow, absoluteCutoffSection);
    }

    public int getAbsoluteCutoffSection() {
        return absoluteCutoffSection;
    }

    public boolean isLosEnabled() {
        return losEnabled;
    }

    public double getLosMaxDistance() {
        return losMaxDistance;
    }

    public int getLosCheckIntervalTicks() {
        return losCheckIntervalTicks;
    }
}
