// dev/doom/customauth/events/AuthListener.java
package dev.doom.customauth.events;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
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

        // Schedule using appropriate scheduler
        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> handlePlayerJoin(player), () -> {});
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, 
                () -> handlePlayerJoin(player));
        }
    }

    private void handlePlayerJoin(Player player) {
        String username = player.getName().toLowerCase();

        // Check IP ban
        if (!plugin.getSecurityUtils().isIpAllowed(player.getAddress().getAddress())) {
            player.kickPlayer(plugin.getLanguageManager().getMessage("security.ip_banned"));
            return;
        }

        // Check session
        if (plugin.getSessionManager().hasValidSession(player)) {
            plugin.getSessionManager().resumeSession(player);
            return;
        }

        // Hide player if configured
        if (plugin.getConfig().getBoolean("security.hide_unauthed_players")) {
            plugin.getServer().getOnlinePlayers().forEach(p -> p.hidePlayer(plugin, player));
        }

        // Check registration status
        boolean isRegistered = plugin.getDatabase() != null ? 
            plugin.getDatabase().isRegistered(username).join() :
            plugin.getFileStorage().isRegistered(username);

        if (!isRegistered) {
            player.sendMessage(plugin.getLanguageManager().getMessage("register.required"));
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("login.required"));
        }

        // Start authentication timeout
        startAuthenticationTimeout(player);

        // Teleport to spawn if configured
        if (plugin.getConfig().getBoolean("spawn.teleport_on_join")) {
            if (plugin.isFolia()) {
                player.getScheduler().run(plugin, task -> 
                    player.teleport(plugin.getConfigManager().getSpawnLocation()), () -> {});
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.teleport(plugin.getConfigManager().getSpawnLocation()));
            }
        }
    }

    private void startAuthenticationTimeout(Player player) {
        long timeout = plugin.getConfig().getLong("login.timeout");
        
        if (plugin.isFolia()) {
            player.getScheduler().runDelayed(plugin, task -> {
                if (player.isOnline() && !isAuthenticated(player)) {
                    player.kick(plugin.getLanguageManager().getMessage("login.timeout"));
                }
            }, () -> {}, Duration.ofSeconds(timeout));
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !isAuthenticated(player)) {
                    player.kickPlayer(plugin.getLanguageManager().getMessage("login.timeout"));
                }
            }, timeout * 20L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String username = player.getName().toLowerCase();

        // Save final data
        PlayerData data = plugin.getCachedPlayerData(username);
        if (data != null) {
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().updateLoginData(username, data.getLastIp(), data.getLastLogin());
            } else {
                plugin.getFileStorage().queueSave(data);
            }
        }

        // Clear cache
        plugin.getPlayerCache().invalidate(username);

        // Remove from hidden players list if necessary
        if (plugin.getConfig().getBoolean("security.hide_unauthed_players")) {
            plugin.getServer().getOnlinePlayers().forEach(p -> p.showPlayer(plugin, player));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerKick(PlayerKickEvent event) {
        // Handle same as quit
        onPlayerQuit(new PlayerQuitEvent(event.getPlayer(), event.getLeaveMessage()));
    }

    private boolean isAuthenticated(Player player) {
        PlayerData data = plugin.getCachedPlayerData(player.getName().toLowerCase());
        return data != null && data.isLoggedIn();
    }
                }
