package com.sektor.verticalantiesp.config;

import com.sektor.verticalantiesp.VerticalAntiESP;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final VerticalAntiESP plugin;

    private double hideDistanceY;
    private double showDistanceY;
    private int checkIntervalTicks;

    private boolean hidePlayers;
    private boolean hideMonsters;
    private boolean hideAnimals;
    private boolean hideMisc;
    private boolean dampenUndergroundSounds;
    private boolean debug;

    public ConfigManager(VerticalAntiESP plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.hideDistanceY = config.getDouble("hide-distance-y", 48.0);
        this.showDistanceY = config.getDouble("show-distance-y", 42.0);
        this.checkIntervalTicks = Math.max(1, config.getInt("check-interval-ticks", 4));

        this.hidePlayers = config.getBoolean("targets.hide-players", true);
        this.hideMonsters = config.getBoolean("targets.hide-monsters", true);
        this.hideAnimals = config.getBoolean("targets.hide-animals", false);
        this.hideMisc = config.getBoolean("targets.hide-misc", true);

        this.dampenUndergroundSounds = config.getBoolean("dampen-underground-sounds", true);
        this.debug = config.getBoolean("debug", false);

        // Sanity check for hysteresis
        if (this.showDistanceY >= this.hideDistanceY) {
            this.showDistanceY = this.hideDistanceY - 4.0;
            plugin.getLogger().warning("show-distance-y was >= hide-distance-y! Auto-adjusted show-distance-y to " + this.showDistanceY);
        }
    }

    public double getHideDistanceY() {
        return hideDistanceY;
    }

    public double getShowDistanceY() {
        return showDistanceY;
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

    public boolean isDampenUndergroundSounds() {
        return dampenUndergroundSounds;
    }

    public boolean isDebug() {
        return debug;
    }
}
