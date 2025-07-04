// dev/doom/customauth/migration/MigrationManager.java
package dev.doom.customauth.migration;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.CompletableFuture;

public class MigrationManager {
    private final CustomAuth plugin;

    public MigrationManager(CustomAuth plugin) {
        this.plugin = plugin;
    }

    /**
     * Migrate data from AuthMe
     */
    public CompletableFuture<MigrationResult> migrateFromAuthMe() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File authMeFolder = new File(plugin.getServer().getPluginManager()
                    .getPlugin("AuthMe").getDataFolder(), "");
                
                if (!authMeFolder.exists()) {
                    return new MigrationResult(false, "AuthMe data folder not found");
                }

                int migrated = 0;
                int failed = 0;

                // Try SQLite first
                File authMeDb = new File(authMeFolder, "authme.db");
                if (authMeDb.exists()) {
                    try (Connection conn = java.sql.DriverManager.getConnection(
                            "jdbc:sqlite:" + authMeDb.getAbsolutePath())) {
                        migrated = migrateFromSQLite(conn);
                    }
                } else {
                    // Try MySQL settings
                    YamlConfiguration authMeConfig = YamlConfiguration.loadConfiguration(
                        new File(authMeFolder, "config.yml"));
                    if (authMeConfig.getBoolean("DataSource.mysql")) {
                        migrated = migrateFromMySQL(authMeConfig);
                    }
                }

                return new MigrationResult(true, 
                    String.format("Migrated %d accounts, %d failed", migrated, failed));
            } catch (Exception e) {
                return new MigrationResult(false, "Migration failed: " + e.getMessage());
            }
        });
    }

    private int migrateFromSQLite(Connection authMeConn) throws Exception {
        int migrated = 0;
        try (PreparedStatement stmt = authMeConn.prepareStatement(
                "SELECT username, password, email, ip, lastlogin FROM authme")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                if (migratePlayer(
                    rs.getString("username"),
                    rs.getString("password"),
                    rs.getString("email"),
                    rs.getString("ip"),
                    rs.getLong("lastlogin"))) {
                    migrated++;
                }
            }
        }
        return migrated;
    }

    private int migrateFromMySQL(YamlConfiguration config) throws Exception {
        String host = config.getString("DataSource.mySQLHost");
        String port = config.getString("DataSource.mySQLPort");
        String database = config.getString("DataSource.mySQLDatabase");
        String username = config.getString("DataSource.mySQLUsername");
        String password = config.getString("DataSource.mySQLPassword");

        try (Connection conn = java.sql.DriverManager.getConnection(
                String.format("jdbc:mysql://%s:%s/%s", host, port, database),
                username, password)) {
            return migrateFromSQLite(conn);
        }
    }

    private boolean migratePlayer(String username, String password, String email, 
                                String ip, long lastLogin) {
        try {
            PlayerData data = new PlayerData(username.toLowerCase(), password);
            data.setEmail(email);
            data.setLastIp(ip);
            data.setLastLogin(lastLogin);
            
            if (plugin.getDatabase() != null) {
                return plugin.getDatabase().registerPlayer(
                    username.toLowerCase(), password, email).join();
            } else {
                plugin.getFileStorage().queueSave(data);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to migrate player " + username + ": " + e.getMessage());
            return false;
        }
    }
}

record MigrationResult(boolean success, String message) {}
