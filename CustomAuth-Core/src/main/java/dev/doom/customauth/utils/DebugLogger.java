// dev/doom/customauth/utils/DebugLogger.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import java.util.logging.Level;
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

        // Log to console if configured
        if (plugin.getConfig().getBoolean("debug.console_output", true)) {
            plugin.getLogger().info("[Debug] " + message);
        }

        // Log to file
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

    public void logLogin(String username, String ip, boolean success) {
        if (!plugin.getConfig().getBoolean("debug.log_login_attempts")) return;

        log(String.format("Login attempt: user=%s, ip=%s, success=%s",
            username, plugin.getSecurityUtils().maskIp(ip), success));
    }

    public void logIpBan(String ip, long duration) {
        if (!plugin.getConfig().getBoolean("debug.log_ip_bans")) return;

        log(String.format("IP banned: ip=%s, duration=%d seconds",
            plugin.getSecurityUtils().maskIp(ip), duration / 1000));
    }

    public void cleanup() {
        if (!debugEnabled) return;

        // Keep last 5 days of logs
        File[] oldLogs = plugin.getDataFolder().listFiles((dir, name) -> 
            name.startsWith("debug") && name.endsWith(".log"));

        if (oldLogs != null && oldLogs.length > 5) {
            for (int i = 0; i < oldLogs.length - 5; i++) {
                oldLogs[i].delete();
            }
        }
    }
}
