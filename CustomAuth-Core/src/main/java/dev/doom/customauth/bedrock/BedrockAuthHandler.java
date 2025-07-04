// dev/doom/customauth/bedrock/BedrockAuthHandler.java
package dev.doom.customauth.bedrock;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.concurrent.CompletableFuture;

public class BedrockAuthHandler implements Listener {
    private final CustomAuth plugin;
    private final FloodgateApi floodgateApi;
    private final boolean floodgateEnabled;

    public BedrockAuthHandler(CustomAuth plugin) {
        this.plugin = plugin;
        this.floodgateEnabled = plugin.getServer().getPluginManager().getPlugin("floodgate") != null;
        this.floodgateApi = floodgateEnabled ? FloodgateApi.getInstance() : null;
        
        if (floodgateEnabled) {
            plugin.getLogger().info("Floodgate detected - Enabling automatic Bedrock authentication");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBedrockPlayerJoin(PlayerJoinEvent event) {
        if (!floodgateEnabled) return;

        Player player = event.getPlayer();
        if (floodgateApi.isFloodgatePlayer(player.getUniqueId())) {
            // Use Folia-compatible scheduling
            if (plugin.isFolia()) {
                player.getScheduler().run(plugin, task -> handleBedrockAuth(player), () -> {});
            } else {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, 
                    () -> handleBedrockAuth(player));
            }
        }
    }

    private void handleBedrockAuth(Player player) {
        String username = player.getName().toLowerCase();
        FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(player.getUniqueId());
        String xuid = floodgatePlayer.getXuid();

        CompletableFuture<Void> authFuture = CompletableFuture.runAsync(() -> {
            // Check if player exists
            PlayerData existingData = plugin.getDatabase() != null ? 
                plugin.getDatabase().getPlayerData(username).join().orElse(null) :
                plugin.getFileStorage().loadPlayer(username);

            if (existingData == null) {
                // Auto-register new Bedrock player
                String secureToken = plugin.getSecurityManager().generateSecureToken(xuid);
                PlayerData newData = new PlayerData(username, secureToken);
                newData.setBedrockPlayer(true);
                newData.setXuid(xuid);
                newData.setLastLogin(System.currentTimeMillis());
                newData.setLastIp(player.getAddress().getAddress().getHostAddress());
                newData.setVerified(true);

                if (plugin.getDatabase() != null) {
                    plugin.getDatabase().registerBedrockPlayer(username, secureToken, xuid);
                } else {
                    plugin.getFileStorage().queueSave(newData);
                }

                plugin.cachePlayerData(username, newData);
                player.sendMessage(plugin.getConfigManager().getMessage("bedrock.auto_register"));
            } else {
                // Verify existing Bedrock player
                if (existingData.isBedrockPlayer() && existingData.getXuid().equals(xuid)) {
                    existingData.setLoggedIn(true);
                    existingData.setLastLogin(System.currentTimeMillis());
                    existingData.setLastIp(player.getAddress().getAddress().getHostAddress());
                    plugin.cachePlayerData(username, existingData);
                    player.sendMessage(plugin.getConfigManager().getMessage("bedrock.auto_login"));
                } else {
                    player.kickPlayer(plugin.getConfigManager().getMessage("bedrock.account_conflict"));
                }
            }
        });

        // Handle any errors
        authFuture.exceptionally(throwable -> {
            plugin.getLogger().severe("Error handling Bedrock authentication: " + throwable.getMessage());
            player.kickPlayer(plugin.getConfigManager().getMessage("error.auth_failed"));
            return null;
        });
    }
}
