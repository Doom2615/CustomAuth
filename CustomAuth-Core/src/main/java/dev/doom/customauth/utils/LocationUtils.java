// dev/doom/customauth/utils/LocationUtils.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import org.bukkit.Location;
import org.bukkit.World;

public class LocationUtils {
    private final CustomAuth plugin;

    public LocationUtils(CustomAuth plugin) {
        this.plugin = plugin;
    }

    public Location getSpawnLocation() {
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        World world = plugin.getServer().getWorld(worldName);
        
        if (world == null) {
            world = plugin.getServer().getWorlds().get(0);
        }

        return new Location(
            world,
            plugin.getConfig().getDouble("spawn.x", 0),
            plugin.getConfig().getDouble("spawn.y", 64),
            plugin.getConfig().getDouble("spawn.z", 0),
            (float) plugin.getConfig().getDouble("spawn.yaw", 0),
            (float) plugin.getConfig().getDouble("spawn.pitch", 0)
        );
    }

    public void saveSpawnLocation(Location location) {
        plugin.getConfig().set("spawn.world", location.getWorld().getName());
        plugin.getConfig().set("spawn.x", location.getX());
        plugin.getConfig().set("spawn.y", location.getY());
        plugin.getConfig().set("spawn.z", location.getZ());
        plugin.getConfig().set("spawn.yaw", location.getYaw());
        plugin.getConfig().set("spawn.pitch", location.getPitch());
        plugin.saveConfig();
    }
}
