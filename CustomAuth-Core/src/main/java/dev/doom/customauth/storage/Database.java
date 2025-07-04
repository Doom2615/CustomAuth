// dev/doom/customauth/storage/Database.java
package dev.doom.customauth.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import dev.doom.customauth.models.SessionData;

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
            setupDataSource();
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        
        if (plugin.getConfig().getBoolean("storage.mysql.enabled")) {
            // MySQL configuration
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                plugin.getConfig().getString("storage.mysql.host"),
                plugin.getConfig().getInt("storage.mysql.port"),
                plugin.getConfig().getString("storage.mysql.database")));
            config.setUsername(plugin.getConfig().getString("storage.mysql.username"));
            config.setPassword(plugin.getConfig().getString("storage.mysql.password"));
            config.setMaximumPoolSize(plugin.getConfig().getInt("storage.mysql.pool-size", 10));
            
            // MySQL optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
        } else {
            // SQLite configuration
            config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/database.db");
            config.setMaximumPoolSize(1); // SQLite doesn't support multiple connections well
        }

        config.setPoolName("CustomAuth-Pool");
        dataSource = new HikariDataSource(config);
    }

    private void createTables() {
        String[] queries = {
            // Players table
            """
            CREATE TABLE IF NOT EXISTS players (
                username VARCHAR(16) PRIMARY KEY,
                password VARCHAR(256) NOT NULL,
                email VARCHAR(100),
                last_ip VARCHAR(45),
                last_login BIGINT,
                registered_date BIGINT,
                verified BOOLEAN DEFAULT FALSE,
                is_bedrock BOOLEAN DEFAULT FALSE,
                xuid VARCHAR(32),
                device_id VARCHAR(64),
                device_os VARCHAR(32),
                verification_token VARCHAR(64)
            )
            """,
            
            // Sessions table
            """
            CREATE TABLE IF NOT EXISTS sessions (
                username VARCHAR(16),
                token VARCHAR(64),
                expires BIGINT,
                ip VARCHAR(45),
                PRIMARY KEY (username, token)
            )
            """,
            
            // IP history table
            """
            CREATE TABLE IF NOT EXISTS ip_history (
                ip VARCHAR(45),
                username VARCHAR(16),
                last_used BIGINT,
                login_count INT DEFAULT 1,
                PRIMARY KEY (ip, username)
            )
            """
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String query : queries) {
                stmt.executeUpdate(query);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
            e.printStackTrace();
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
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> registerBedrockPlayer(String username, String token, 
                                                          String xuid, String deviceId, String deviceOs) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO players (
                    username, password, registered_date, verified, is_bedrock, 
                    xuid, device_id, device_os
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                stmt.setString(2, token);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setBoolean(4, true);
                stmt.setBoolean(5, true);
                stmt.setString(6, xuid);
                stmt.setString(7, deviceId);
                stmt.setString(8, deviceOs);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to register Bedrock player: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
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
                    data.setBedrockPlayer(rs.getBoolean("is_bedrock"));
                    data.setXuid(rs.getString("xuid"));
                    data.setDeviceId(rs.getString("device_id"));
                    data.setDeviceOs(rs.getString("device_os"));
                    return Optional.of(data);
                }
                return Optional.empty();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get player data: " + e.getMessage());
                return Optional.empty();
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> updateLoginData(String username, String ip, long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Update player data
                String sql1 = "UPDATE players SET last_ip = ?, last_login = ? WHERE username = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                    stmt.setString(1, ip);
                    stmt.setLong(2, timestamp);
                    stmt.setString(3, username.toLowerCase());
                    stmt.executeUpdate();
                }

                // Update IP history
                String sql2 = """
                    INSERT INTO ip_history (ip, username, last_used, login_count)
                    VALUES (?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                    last_used = VALUES(last_used),
                    login_count = login_count + 1
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                    stmt.setString(1, ip);
                    stmt.setString(2, username.toLowerCase());
                    stmt.setLong(3, timestamp);
                    stmt.executeUpdate();
                }

                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update login data: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> updateBedrockDeviceInfo(String username, String deviceId, String deviceOs) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE players SET device_id = ?, device_os = ? WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, deviceId);
                stmt.setString(2, deviceOs);
                stmt.setString(3, username.toLowerCase());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update Bedrock device info: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> deletePlayer(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM players WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete player: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    public CompletableFuture<Boolean> saveSession(String username, String token, long expiry, String ip) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO sessions (username, token, expires, ip) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE token = ?, expires = ?, ip = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                stmt.setString(2, token);
                stmt.setLong(3, expiry);
                stmt.setString(4, ip);
                stmt.setString(5, token);
                stmt.setLong(6, expiry);
                stmt.setString(7, ip);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save session: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> updateSessionExpiry(String username, String token, long expiry) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE sessions SET expires = ? WHERE username = ? AND token = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, expiry);
                stmt.setString(2, username.toLowerCase());
                stmt.setString(3, token);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update session expiry: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> deleteSession(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM sessions WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete session: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> deleteAllSessions(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM sessions WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete all sessions: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<SessionData> getSession(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM sessions WHERE username = ? AND expires > ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                stmt.setLong(2, System.currentTimeMillis());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return new SessionData(
                        rs.getString("username"),
                        rs.getString("token"),
                        rs.getLong("expires"),
                        rs.getString("ip"),
                        plugin.getServer().getOfflinePlayer(username).getUniqueId(),
                        System.currentTimeMillis()
                    );
                }
                return null;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get session: " + e.getMessage());
                return null;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> cleanupExpiredSessions(long currentTime) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM sessions WHERE expires < ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, currentTime);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to cleanup expired sessions: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> updatePassword(String username, String hashedPassword) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE players SET password = ? WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hashedPassword);
                stmt.setString(2, username.toLowerCase());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update password: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> reset2FA(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE players SET verified = false, verification_token = NULL WHERE username = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to reset 2FA: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> cleanup() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Cleanup expired sessions
                try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM sessions WHERE expires < ?")) {
                    stmt.setLong(1, System.currentTimeMillis());
                    stmt.executeUpdate();
                }

                // Cleanup old verification tokens
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE players SET verification_token = NULL WHERE verification_token IS NOT NULL AND last_login < ?")) {
                    stmt.setLong(1, System.currentTimeMillis() - (24 * 60 * 60 * 1000)); // 24 hours
                    stmt.executeUpdate();
                }

                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to perform cleanup: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }
 }
