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

/**
 * Loads config.yml and caches everything the packet-thread listener needs as
 * plain, already-resolved, thread-safe values. ChunkPacketListener must never
 * touch the Bukkit API directly while processing a packet off the main
 * thread - this class is where that resolution happens instead, safely, on
 * the main thread (config load, world load/unload events).
 */
public final class ConfigManager implements Listener {

    private final Main plugin;

    private volatile int subChunksBelow;
    // CLAUDE'UN EKLETTİĞİ YENİ ALAN BURADA
    private volatile int absoluteCutoffSection;
    private volatile boolean debugMode;
    private volatile int refreshRadius;
    private volatile long refreshCooldownMs;
    private volatile Set<String> enabledWorlds = Collections.emptySet();

    // World name -> lowest section index (world.getMinHeight() >> 4).
    // Resolved on the main thread whenever a world loads; read on the packet thread.
    private final Map<String, Integer> worldMinSection = new ConcurrentHashMap<>();

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        this.subChunksBelow = Math.max(0, cfg.getInt("sub-chunks-below", 2));
        // CLAUDE'UN EKLETTİĞİ AYARI OKUMA KISMI BURADA
        this.absoluteCutoffSection = cfg.getInt("absolute-cutoff-y", 0) >> 4;
        
        this.debugMode = cfg.getBoolean("debug-mode", false);
        this.refreshRadius = Math.max(1, cfg.getInt("refresh-radius", 3));
        this.refreshCooldownMs = Math.max(0L, cfg.getLong("refresh-cooldown-ms", 250L));

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

    /**
     * Lowest section index for a world (e.g. -4 for an overworld with
     * min-height -64, 0 for the Nether/End). Falls back to -4 if the world
     * hasn't been cached yet - safer to assume overworld-shaped than to skip
     * culling for that packet entirely.
     */
    public int getMinSection(String worldName) {
        Integer cached = worldMinSection.get(worldName);
        return cached != null ? cached : -4;
    }

    public int getSubChunksBelow() {
        return subChunksBelow;
    }

    // CLAUDE'UN EKLETTİĞİ GETTER METODU BURADA
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
}
