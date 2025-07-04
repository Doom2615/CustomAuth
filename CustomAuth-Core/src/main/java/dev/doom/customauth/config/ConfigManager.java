// dev/doom/customauth/config/ConfigManager.java
package dev.doom.customauth.config;

import dev.doom.customauth.CustomAuth;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final CustomAuth plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(CustomAuth plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfigs() {
        loadConfig();
        plugin.getLanguageManager().reloadMessages();
    }

    public Location getSpawnLocation() {
        String worldName = config.getString("spawn.world", "world");
        World world = plugin.getServer().getWorld(worldName);
        
        if (world == null) {
            world = plugin.getServer().getWorlds().get(0);
        }

        return new Location(
            world,
            config.getDouble("spawn.x", 0),
            config.getDouble("spawn.y", 64),
            config.getDouble("spawn.z", 0),
            (float) config.getDouble("spawn.yaw", 0),
            (float) config.getDouble("spawn.pitch", 0)
        );
    }

    public void saveSpawnLocation(Location location) {
        config.set("spawn.world", location.getWorld().getName());
        config.set("spawn.x", location.getX());
        config.set("spawn.y", location.getY());
        config.set("spawn.z", location.getZ());
        config.set("spawn.yaw", location.getYaw());
        config.set("spawn.pitch", location.getPitch());
        saveConfig();
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config to " + configFile);
        }
    }
}
