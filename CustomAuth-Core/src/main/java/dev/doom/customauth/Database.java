// Database.java - Optimized database handling
package dev.doom.customauth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Database {
    private final CustomAuth plugin;
    private HikariDataSource dataSource;

    public Database(CustomAuth plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            HikariConfig config = new HikariConfig();
            
            if (plugin.getConfig().getBoolean("mysql.enabled")) {
                config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                    plugin.getConfig().getString("mysql.host"),
                    plugin.getConfig().getInt("mysql.port"),
                    plugin.getConfig().getString("mysql.database")));
                config.setUsername(plugin.getConfig().getString("mysql.username"));
                config.setPassword(plugin.getConfig().getString("mysql.password"));
                config.setMaximumPoolSize(plugin.getConfig().getInt("mysql.pool-size", 10));
            } else {
                config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/database.db");
                config.setMaximumPoolSize(1);
            }

            config.setPoolName("CustomAuth-Pool");
            dataSource = new HikariDataSource(config);
            
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    private void createTables() {
        String[] queries = {
            "CREATE TABLE IF NOT EXISTS players (" +
                "username VARCHAR(16) PRIMARY KEY," +
                "password VARCHAR(256) NOT NULL," +
                "email VARCHAR(100)," +
                "last_ip VARCHAR(45)," +
                "last_login BIGINT," +
                "registered_date BIGINT," +
                "verified BOOLEAN DEFAULT FALSE," +
                "verification_token VARCHAR(64)" +
            ")",
            
            "CREATE TABLE IF NOT EXISTS sessions (" +
                "username VARCHAR(16)," +
                "token VARCHAR(64)," +
                "expires BIGINT," +
                "PRIMARY KEY (username, token)" +
            ")",
            
            "CREATE TABLE IF NOT EXISTS ip_history (" +
                "ip VARCHAR(45)," +
                "username VARCHAR(16)," +
                "last_used BIGINT," +
                "PRIMARY KEY (ip, username)" +
            ")"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String query : queries) {
                stmt.executeUpdate(query);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    public CompletableFuture<Boolean> registerPlayer(String username, String hashedPassword, String email) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO players (username, password, email, registered_date) VALUES (?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                stmt.setString(2, hashedPassword);
                stmt.setString(3, email);
                stmt.setLong(4, System.currentTimeMillis());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to register player: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Optional<PlayerData>> getPlayerData(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM players WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    PlayerData data = new PlayerData(
                        rs.getString("username"),
                        rs.getString("password")
                    );
                    data.setEmail(rs.getString("email"));
                    data.setLastIp(rs.getString("last_ip"));
                    data.setLastLogin(rs.getLong("last_login"));
                    data.setVerified(rs.getBoolean("verified"));
                    return Optional.of(data);
                }
                return Optional.empty();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get player data: " + e.getMessage());
                return Optional.empty();
            }
        });
    }

    public CompletableFuture<Boolean> updateLoginData(String username, String ip, long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE players SET last_ip = ?, last_login = ? WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ip);
                stmt.setLong(2, timestamp);
                stmt.setString(3, username.toLowerCase());
                
                // Also update IP history
                updateIpHistory(conn, username, ip);
                
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update login data: " + e.getMessage());
                return false;
            }
        });
    }

    private void updateIpHistory(Connection conn, String username, String ip) throws SQLException {
        String sql = "INSERT INTO ip_history (ip, username, last_used) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE last_used = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setString(1, ip);
            stmt.setString(2, username.toLowerCase());
            stmt.setLong(3, now);
            stmt.setLong(4, now);
            stmt.executeUpdate();
        }
    }

    public void saveSession(String username, String token, long expiry) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO sessions (username, token, expires) VALUES (?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                stmt.setString(2, token);
                stmt.setLong(3, expiry);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save session: " + e.getMessage());
            }
        });
    }

    public void cleanupExpiredSessions(long currentTime) {
        CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM sessions WHERE expires < ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, currentTime);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to cleanup sessions: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
