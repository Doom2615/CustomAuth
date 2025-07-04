// dev/doom/customauth/utils/DataConverter.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.CompletableFuture;

public class DataConverter {
    private final CustomAuth plugin;

    public DataConverter(CustomAuth plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<ConversionResult> convertFromAuthMe() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File authMeFolder = new File(plugin.getServer().getPluginManager()
                    .getPlugin("AuthMe").getDataFolder(), "");
                
                if (!authMeFolder.exists()) {
                    return new ConversionResult(false, "AuthMe data folder not found");
                }

                int converted = 0;
                int failed = 0;

                // Try SQLite first
                File authMeDb = new File(authMeFolder, "authme.db");
                if (authMeDb.exists()) {
                    try (Connection conn = DriverManager.getConnection(
                            "jdbc:sqlite:" + authMeDb.getAbsolutePath())) {
                        converted = convertFromSQLite(conn);
                    }
                } else {
                    // Try MySQL settings
                    YamlConfiguration authMeConfig = YamlConfiguration.loadConfiguration(
                        new File(authMeFolder, "config.yml"));
                    if (authMeConfig.getBoolean("DataSource.mysql")) {
                        converted = convertFromMySQL(authMeConfig);
                    }
                }

                return new ConversionResult(true, 
                    String.format("Converted %d accounts, %d failed", converted, failed));
            } catch (Exception e) {
                return new ConversionResult(false, "Conversion failed: " + e.getMessage());
            }
        }, plugin.getAsyncExecutor());
    }

    private int convertFromSQLite(Connection authMeConn) throws Exception {
        int converted = 0;
        var stmt = authMeConn.prepareStatement(
            "SELECT username, password, email, ip, lastlogin FROM authme");
        var rs = stmt.executeQuery();

        while (rs.next()) {
            PlayerData data = new PlayerData(
                rs.getString("username").toLowerCase(),
                rs.getString("password")
            );
            data.setEmail(rs.getString("email"));
            data.setLastIp(rs.getString("ip"));
            data.setLastLogin(rs.getLong("lastlogin"));

            if (plugin.getDatabase() != null) {
                if (plugin.getDatabase().registerPlayer(
                    data.getUsername(), data.getHashedPassword(), data.getEmail()).join()) {
                    converted++;
                }
            } else {
                plugin.getFileStorage().queueSave(data);
                converted++;
            }
        }

        return converted;
    }

    private int convertFromMySQL(YamlConfiguration config) throws Exception {
        String host = config.getString("DataSource.mySQLHost");
        String port = config.getString("DataSource.mySQLPort");
        String database = config.getString("DataSource.mySQLDatabase");
        String username = config.getString("DataSource.mySQLUsername");
        String password = config.getString("DataSource.mySQLPassword");

        try (Connection conn = DriverManager.getConnection(
                String.format("jdbc:mysql://%s:%s/%s", host, port, database),
                username, password)) {
            return convertFromSQLite(conn);
        }
    }

    public record ConversionResult(boolean success, String message) {}
          }
