// dev/doom/customauth/handlers/AuthListener.java
package dev.doom.customauth.handlers;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.time.Duration;

public class AuthListener implements Listener {
    private final CustomAuth plugin;

    public AuthListener(CustomAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Skip if player is Bedrock (handled by BedrockAuthHandler)
        if (plugin.getBedrockAuthHandler() != null && 
            plugin.getBedrockAuthHandler().isBedrockPlayer(player)) {
            return;
        }

        // Use appropriate scheduler based on server type
        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> handlePlayerJoin(player), () -> {});
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, 
                () -> handlePlayerJoin(player));
        }
    }

    private void handlePlayerJoin(Player player) {
        String username = player.getName().toLowerCase();

        // Check for session
        if (plugin.getSessionManager().hasValidSession(player)) {
            plugin.getSessionManager().resumeSession(player);
            return;
        }

        // Check registration status
        boolean isRegistered = plugin.getDatabase() != null ? 
            plugin.getDatabase().isRegistered(username) :
            plugin.getFileStorage().loadPlayer(username) != null;

        if (!isRegistered) {
            player.sendMessage(plugin.getConfigManager().getMessage("register.required"));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("login.required"));
        }

        // Start authentication timeout
        startAuthenticationTimeout(player);
    }

    private void startAuthenticationTimeout(Player player) {
        long timeout = plugin.getConfig().getLong("security.login_timeout");
        
        if (plugin.isFolia()) {
            player.getScheduler().runDelayed(plugin, task -> {
                if (player.isOnline() && !isAuthenticated(player)) {
                    player.kick(plugin.getConfigManager().getMessage("login.timeout"));
                }
            }, () -> {}, Duration.ofSeconds(timeout));
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !isAuthenticated(player)) {
                    player.kick(plugin.getConfigManager().getMessage("login.timeout"));
                }
            }, timeout * 20L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String username = event.getPlayer().getName().toLowerCase();
        PlayerData data = plugin.getCachedPlayerData(username);
        
        if (data != null) {
            // Save final data
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().updateLoginData(username, data.getLastIp(), data.getLastLogin());
            } else {
                plugin.getFileStorage().queueSave(data);
            }
            
            // Clear cache
            plugin.getPlayerCache().invalidate(username);
        }
    }

    private boolean isAuthenticated(Player player) {
        PlayerData data = plugin.getCachedPlayerData(player.getName().toLowerCase());
        return data != null && data.isLoggedIn();
    }
}
