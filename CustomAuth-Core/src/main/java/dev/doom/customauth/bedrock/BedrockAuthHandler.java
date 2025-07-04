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
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class BedrockAuthHandler implements Listener {
    private final CustomAuth plugin;
    private final FloodgateApi floodgateApi;
    private final Map<UUID, BedrockPlayerData> bedrockPlayers;
    private final boolean floodgateEnabled;

    public BedrockAuthHandler(CustomAuth plugin) {
        this.plugin = plugin;
        this.bedrockPlayers = new ConcurrentHashMap<>();
        this.floodgateEnabled = plugin.getServer().getPluginManager().getPlugin("floodgate") != null;
        this.floodgateApi = floodgateEnabled ? FloodgateApi.getInstance() : null;

        if (floodgateEnabled) {
            plugin.getLogger().info("Bedrock support initialized with Floodgate");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBedrockPlayerJoin(PlayerJoinEvent event) {
        if (!floodgateEnabled) return;

        Player player = event.getPlayer();
        if (!floodgateApi.isFloodgatePlayer(player.getUniqueId())) return;

        // Use appropriate scheduler based on server type
        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> handleBedrockAuth(player), () -> {});
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, 
                () -> handleBedrockAuth(player));
        }
    }

    private void handleBedrockAuth(Player player) {
        FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(player.getUniqueId());
        String username = player.getName().toLowerCase();
        String xuid = floodgatePlayer.getXuid();
        String deviceOs = floodgatePlayer.getDeviceOs().toString();
        String deviceId = floodgatePlayer.getDeviceId();

        BedrockPlayerData bedrockData = new BedrockPlayerData(
            username,
            xuid,
            deviceOs,
            deviceId,
            floodgatePlayer.getLanguageCode()
        );

        // Check for existing account
        PlayerData existingData = plugin.getCachedPlayerData(username);
        if (existingData == null) {
            existingData = plugin.getDatabase() != null ?
                plugin.getDatabase().getPlayerData(username).join().orElse(null) :
                plugin.getFileStorage().loadPlayer(username);
        }

        if (existingData == null) {
            // New Bedrock player - auto register
            handleNewBedrockPlayer(player, bedrockData);
        } else if (existingData.isBedrockPlayer()) {
            // Existing Bedrock player - verify and login
            handleExistingBedrockPlayer(player, existingData, bedrockData);
        } else {
            // Account conflict with Java player
            handleAccountConflict(player);
        }

        bedrockPlayers.put(player.getUniqueId(), bedrockData);
    }

    private void handleNewBedrockPlayer(Player player, BedrockPlayerData bedrockData) {
        String secureToken = plugin.getSecurityUtils().generateSecureToken(
            bedrockData.xuid() + bedrockData.deviceId()
        );

        PlayerData newData = new PlayerData(bedrockData.username(), secureToken);
        newData.setBedrockPlayer(true);
        newData.setXuid(bedrockData.xuid());
        newData.setLastLogin(System.currentTimeMillis());
        newData.setLastIp(player.getAddress().getAddress().getHostAddress());
        newData.setVerified(true);
        newData.setLoggedIn(true);

        // Save to appropriate storage
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().registerBedrockPlayer(
                bedrockData.username(),
                secureToken,
                bedrockData.xuid(),
                bedrockData.deviceId(),
                bedrockData.deviceOs()
            );
        } else {
            plugin.getFileStorage().queueSave(newData);
        }

        plugin.cachePlayerData(bedrockData.username(), newData);
        player.sendMessage(plugin.getLanguageManager().getMessage("bedrock.auto_register"));
    }

    private void handleExistingBedrockPlayer(Player player, PlayerData existingData, 
                                           BedrockPlayerData bedrockData) {
        if (existingData.getXuid().equals(bedrockData.xuid())) {
            // Valid Bedrock player - auto login
            existingData.setLoggedIn(true);
            existingData.setLastLogin(System.currentTimeMillis());
            existingData.setLastIp(player.getAddress().getAddress().getHostAddress());
            
            plugin.cachePlayerData(bedrockData.username(), existingData);
            player.sendMessage(plugin.getLanguageManager().getMessage("bedrock.auto_login"));

            // Update device info if changed
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().updateBedrockDeviceInfo(
                    bedrockData.username(),
                    bedrockData.deviceId(),
                    bedrockData.deviceOs()
                );
            }
        } else {
            // XUID mismatch - potential security issue
            handleSecurityMismatch(player);
        }
    }

    private void handleAccountConflict(Player player) {
        player.kickPlayer(plugin.getLanguageManager().getMessage("bedrock.account_conflict"));
        plugin.getLogger().warning("Account conflict detected for: " + player.getName());
    }

    private void handleSecurityMismatch(Player player) {
        player.kickPlayer(plugin.getLanguageManager().getMessage("bedrock.security_mismatch"));
        plugin.getLogger().warning("Security mismatch for Bedrock player: " + player.getName());
    }

    @EventHandler
    public void onBedrockPlayerQuit(PlayerQuitEvent event) {
        bedrockPlayers.remove(event.getPlayer().getUniqueId());
    }

    public boolean isBedrockPlayer(Player player) {
        return floodgateEnabled && floodgateApi.isFloodgatePlayer(player.getUniqueId());
    }

    public BedrockPlayerData getBedrockData(UUID playerUuid) {
        return bedrockPlayers.get(playerUuid);
    }

    public int getActiveBedrockPlayers() {
        return bedrockPlayers.size();
    }
}
