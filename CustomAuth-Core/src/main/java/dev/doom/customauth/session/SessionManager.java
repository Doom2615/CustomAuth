// dev/doom/customauth/session/SessionManager.java
package dev.doom.customauth.session;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import dev.doom.customauth.models.SessionData;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.UUID;

public class SessionManager {
    private final CustomAuth plugin;
    private final Map<String, SessionData> activeSessions;
    private final Map<String, String> sessionTokens;
    private final long sessionTimeout;

    public SessionManager(CustomAuth plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.sessionTokens = new ConcurrentHashMap<>();
        this.sessionTimeout = plugin.getConfig().getLong("security.session_timeout", 7200) * 1000; // Convert to milliseconds
        
        startCleanupTask();
    }

    public void createSession(Player player) {
        if (!plugin.getConfig().getBoolean("session.enabled", true)) {
            return;
        }

        String username = player.getName().toLowerCase();
        String token = plugin.getSecurityUtils().generateToken();
        long expiry = System.currentTimeMillis() + sessionTimeout;
        String ip = player.getAddress().getAddress().getHostAddress();

        SessionData sessionData = new SessionData(
            username,
            token,
            expiry,
            ip,
            player.getUniqueId(),
            System.currentTimeMillis()
        );

        activeSessions.put(username, sessionData);
        sessionTokens.put(token, username);

        // Save session if persistence is enabled
        if (plugin.getConfig().getBoolean("session.persist", true)) {
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().saveSession(username, token, expiry, ip);
            } else {
                plugin.getFileStorage().saveSession(sessionData);
            }
        }

        // Send session duration message
        long hours = sessionTimeout / (1000 * 60 * 60);
        player.sendMessage(plugin.getLanguageManager().getMessage("session.created")
            .replace("%time%", String.valueOf(hours)));
    }

    public boolean hasValidSession(Player player) {
        if (!plugin.getConfig().getBoolean("session.enabled", true)) {
            return false;
        }

        String username = player.getName().toLowerCase();
        SessionData session = activeSessions.get(username);

        // Check memory cache first
        if (session != null) {
            if (isSessionValid(session, player)) {
                return true;
            } else {
                removeSession(username);
                return false;
            }
        }

        // Check persistent storage if enabled
        if (plugin.getConfig().getBoolean("session.persist", true)) {
            session = loadSession(username);
            if (session != null && isSessionValid(session, player)) {
                activeSessions.put(username, session);
                sessionTokens.put(session.token(), username);
                return true;
            }
        }

        return false;
    }

    private boolean isSessionValid(SessionData session, Player player) {
        if (System.currentTimeMillis() > session.expiry()) {
            return false;
        }

        // Validate IP if required
        if (plugin.getConfig().getBoolean("security.validate_ip_on_session", true)) {
            String currentIp = player.getAddress().getAddress().getHostAddress();
            if (!session.ip().equals(currentIp)) {
                plugin.getDebugLogger().log(String.format(
                    "Session IP mismatch for %s: expected=%s, got=%s",
                    player.getName(), session.ip(), currentIp
                ));
                return false;
            }
        }

        // Validate UUID
        if (!session.uuid().equals(player.getUniqueId())) {
            plugin.getDebugLogger().log(String.format(
                "Session UUID mismatch for %s: expected=%s, got=%s",
                player.getName(), session.uuid(), player.getUniqueId()
            ));
            return false;
        }

        return true;
    }

    public void resumeSession(Player player) {
        String username = player.getName().toLowerCase();
        PlayerData data = new PlayerData(username, "");
        data.setLoggedIn(true);
        data.setLastLogin(System.currentTimeMillis());
        data.setLastIp(player.getAddress().getAddress().getHostAddress());
        
        plugin.cachePlayerData(username, data);
        player.sendMessage(plugin.getLanguageManager().getMessage("session.resumed"));

        // Update session expiry
        SessionData session = activeSessions.get(username);
        if (session != null) {
            SessionData updatedSession = new SessionData(
                session.username(),
                session.token(),
                System.currentTimeMillis() + sessionTimeout,
                session.ip(),
                session.uuid(),
                session.createdAt()
            );
            activeSessions.put(username, updatedSession);

            // Update persistent storage if enabled
            if (plugin.getConfig().getBoolean("session.persist", true)) {
                if (plugin.getDatabase() != null) {
                    plugin.getDatabase().updateSessionExpiry(
                        username, session.token(), updatedSession.expiry());
                } else {
                    plugin.getFileStorage().saveSession(updatedSession);
                }
            }
        }
    }

    public void removeSession(String username) {
        SessionData session = activeSessions.remove(username.toLowerCase());
        if (session != null) {
            sessionTokens.remove(session.token());
        }

        // Remove from persistent storage if enabled
        if (plugin.getConfig().getBoolean("session.persist", true)) {
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().deleteSession(username.toLowerCase());
            } else {
                plugin.getFileStorage().deleteSession(username.toLowerCase());
            }
        }
    }

    public void invalidateAllSessions(String username) {
        removeSession(username.toLowerCase());
        
        // Remove all sessions for this username from persistent storage
        if (plugin.getConfig().getBoolean("session.persist", true)) {
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().deleteAllSessions(username.toLowerCase());
            } else {
                plugin.getFileStorage().deleteAllSessions(username.toLowerCase());
            }
        }
    }

    private SessionData loadSession(String username) {
        if (plugin.getDatabase() != null) {
            return plugin.getDatabase().getSession(username.toLowerCase()).join();
        } else {
            return plugin.getFileStorage().loadSession(username.toLowerCase());
        }
    }

    private void startCleanupTask() {
        long cleanupInterval = plugin.getConfig().getLong("session.cleanup_interval", 300) * 20L; // Convert to ticks
        
        plugin.scheduleTask(() -> {
            long now = System.currentTimeMillis();
            
            // Cleanup memory cache
            activeSessions.entrySet().removeIf(entry -> {
                if (entry.getValue().expiry() < now) {
                    sessionTokens.remove(entry.getValue().token());
                    return true;
                }
                return false;
            });

            // Cleanup persistent storage
            if (plugin.getConfig().getBoolean("session.persist", true)) {
                if (plugin.getDatabase() != null) {
                    plugin.getDatabase().cleanupExpiredSessions(now);
                } else {
                    plugin.getFileStorage().cleanupExpiredSessions(now);
                }
            }
        }, cleanupInterval, cleanupInterval);
    }

    public int getActiveSessions() {
        return activeSessions.size();
    }

    public boolean isSessionToken(String token) {
        return sessionTokens.containsKey(token);
    }

    public String getUsernameFromToken(String token) {
        return sessionTokens.get(token);
    }
}
