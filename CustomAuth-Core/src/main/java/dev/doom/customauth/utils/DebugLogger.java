// dev/doom/customauth/utils/DebugLogger.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DebugLogger {
    private final CustomAuth plugin;
    private final boolean debugEnabled;
    private final File logFile;
    private final SimpleDateFormat dateFormat;

    public DebugLogger(CustomAuth plugin) {
        this.plugin = plugin;
        this.debugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
        this.logFile = new File(plugin.getDataFolder(), "debug.log");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if (debugEnabled && !logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create debug log file: " + e.getMessage());
            }
        }
    }

    public void log(String message) {
        if (!debugEnabled) return;

        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("[%s] %s", timestamp, message);

        if (plugin.getConfig().getBoolean("debug.console_output", true)) {
            plugin.getLogger().info("[Debug] " + message);
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(logMessage);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to write to debug log: " + e.getMessage());
        }
    }

    public void logError(String message, Throwable error) {
        if (!debugEnabled) return;
        log("ERROR: " + message);
        log("Stack trace:");
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            error.printStackTrace(writer);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to write error to debug log: " + e.getMessage());
        }
    }
}
