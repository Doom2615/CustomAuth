// dev/doom/customauth/utils/UpdateChecker.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import org.bukkit.Bukkit;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

public class UpdateChecker {
    private final CustomAuth plugin;
    private final int resourceId;

    public UpdateChecker(CustomAuth plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
                try (InputStream inputStream = url.openStream();
                     Scanner scanner = new Scanner(inputStream)) {
                    if (scanner.hasNext()) {
                        String latestVersion = scanner.next();
                        String currentVersion = plugin.getDescription().getVersion();

                        if (!currentVersion.equals(latestVersion)) {
                            plugin.getLogger().info("A new update is available!");
                            plugin.getLogger().info("Current version: " + currentVersion);
                            plugin.getLogger().info("Latest version: " + latestVersion);
                        }
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }
}
