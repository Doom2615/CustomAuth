// dev/doom/customauth/api/CustomAuthAPI.java
package dev.doom.customauth.api;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.entity.Player;
import java.util.concurrent.CompletableFuture;

public class CustomAuthAPI {
    private static CustomAuth plugin;

    public static void setPlugin(CustomAuth customAuth) {
        plugin = customAuth;
    }

    /**
     * Check if a player is registered
     * @param username Player username
     * @return CompletableFuture<Boolean> indicating registration status
     */
    public static CompletableFuture<Boolean> isRegistered(String username) {
        return CompletableFuture.supplyAsync(() -> {
            if (plugin.getDatabase() != null) {
                return plugin.getDatabase().isRegistered(username.toLowerCase());
            }
            return plugin.getFileStorage().isRegistered(username.toLowerCase());
        }, plugin.getAsyncExecutor());
    }

    /**
     * Check if a player is authenticated
     * @param player Bukkit Player object
     * @return boolean indicating authentication status
     */
    public static boolean isAuthenticated(Player player) {
        PlayerData data = plugin.getCachedPlayerData(player.getName().toLowerCase());
        return data != null && data.isLoggedIn();
    }

    /**
     * Force login a player
     * @param player Bukkit Player object
     * @return CompletableFuture<Boolean> indicating success
     */
    public static CompletableFuture<Boolean> forceLogin(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String username = player.getName().toLowerCase();
            PlayerData data = plugin.getCachedPlayerData(username);
            
            if (data == null) {
                data = plugin.getDatabase() != null ? 
                    plugin.getDatabase().getPlayerData(username).join().orElse(null) :
                    plugin.getFileStorage().loadPlayer(username);
                
                if (data == null) return false;
            }

            data.setLoggedIn(true);
            data.setLastLogin(System.currentTimeMillis());
            data.setLastIp(player.getAddress().getAddress().getHostAddress());
            plugin.cachePlayerData(username, data);
            
            // Create session if enabled
            if (plugin.getConfig().getBoolean("session.enabled")) {
                plugin.getSessionManager().createSession(player);
            }

            return true;
        }, plugin.getAsyncExecutor());
    }

    /**
     * Force logout a player
     * @param player Bukkit Player object
     * @return boolean indicating success
     */
    public static boolean forceLogout(Player player) {
        String username = player.getName().toLowerCase();
        PlayerData data = plugin.getCachedPlayerData(username);
        
        if (data != null) {
            data.setLoggedIn(false);
            plugin.cachePlayerData(username, data);
            plugin.getSessionManager().removeSession(username);
            return true;
        }
        return false;
    }

    /**
     * Check if a player is a Bedrock player
     * @param player Bukkit Player object
     * @return boolean indicating if player is from Bedrock
     */
    public static boolean isBedrockPlayer(Player player) {
        return plugin.getBedrockAuthHandler() != null && 
               plugin.getBedrockAuthHandler().isBedrockPlayer(player);
    }

    /**
     * Get player data
     * @param username Player username
     * @return CompletableFuture<PlayerData> containing player data
     */
    public static CompletableFuture<PlayerData> getPlayerData(String username) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = plugin.getCachedPlayerData(username.toLowerCase());
            if (data != null) return data;

            return plugin.getDatabase() != null ? 
                plugin.getDatabase().getPlayerData(username.toLowerCase()).join().orElse(null) :
                plugin.getFileStorage().loadPlayer(username.toLowerCase());
        }, plugin.getAsyncExecutor());
    }

    /**
     * Change a player's password
     * @param username Player username
     * @param newPassword New password
     * @return CompletableFuture<Boolean> indicating success
     */
    public static CompletableFuture<Boolean> changePassword(String username, String newPassword) {
        if (!plugin.getSecurityUtils().isPasswordValid(newPassword)) {
            return CompletableFuture.completedFuture(false);
        }

        String hashedPassword = plugin.getSecurityUtils().hashPassword(newPassword);
        return CompletableFuture.supplyAsync(() -> {
            if (plugin.getDatabase() != null) {
                return plugin.getDatabase().updatePassword(username.toLowerCase(), hashedPassword).join();
            } else {
                PlayerData data = plugin.getFileStorage().loadPlayer(username.toLowerCase());
                if (data != null) {
                    data.setHashedPassword(hashedPassword);
                    plugin.getFileStorage().queueSave(data);
                    return true;
                }
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    /**
     * Get the plugin instance
     * @return CustomAuth plugin instance
     */
    public static CustomAuth getPlugin() {
        return plugin;
    }
}
