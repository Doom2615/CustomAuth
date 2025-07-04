// dev/doom/customauth/commands/LoginCommand.java
package dev.doom.customauth.commands;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class LoginCommand implements CommandExecutor, TabCompleter {
    private final CustomAuth plugin;

    public LoginCommand(CustomAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("error.console_command"));
            return true;
        }

        // Skip if player is Bedrock
        if (plugin.getBedrockAuthHandler() != null && 
            plugin.getBedrockAuthHandler().isBedrockPlayer(player)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("bedrock.already_logged_in"));
            return true;
        }

        // Schedule login process
        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> handleLogin(player, args), () -> {});
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, 
                () -> handleLogin(player, args));
        }

        return true;
    }

    private void handleLogin(Player player, String[] args) {
        String username = player.getName().toLowerCase();

        // Check if registered
        if (plugin.getDatabase() != null ? 
            !plugin.getDatabase().isRegistered(username).join() :
            !plugin.getFileStorage().isRegistered(username)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("login.not_registered"));
            return;
        }

        // Check if already logged in
        PlayerData data = plugin.getCachedPlayerData(username);
        if (data != null && data.isLoggedIn()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("login.already_logged_in"));
            return;
        }

        // Validate arguments
        if (args.length < 1) {
            player.sendMessage(plugin.getLanguageManager().getMessage("login.usage"));
            return;
        }

        // Check login attempts
        if (!plugin.getSecurityUtils().canAttemptLogin(player)) {
            player.kickPlayer(plugin.getLanguageManager().getMessage("login.too_many_attempts"));
            return;
        }

        // Load player data if not cached
        if (data == null) {
            data = plugin.getDatabase() != null ?
                plugin.getDatabase().getPlayerData(username).join().orElse(null) :
                plugin.getFileStorage().loadPlayer(username);

            if (data == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("error.data_not_found"));
                return;
            }
        }

        // Verify password
        if (plugin.getSecurityUtils().checkPassword(args[0], data.getHashedPassword())) {
            handleSuccessfulLogin(player, data);
        } else {
            handleFailedLogin(player);
        }
    }

    private void handleSuccessfulLogin(Player player, PlayerData data) {
        String username = player.getName().toLowerCase();
        
        // Update login data
        data.setLoggedIn(true);
        data.setLastLogin(System.currentTimeMillis());
        data.setLastIp(player.getAddress().getAddress().getHostAddress());
        
        // Update storage
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().updateLoginData(username, data.getLastIp(), data.getLastLogin());
        } else {
            plugin.getFileStorage().updateLoginData(username, data.getLastIp(), data.getLastLogin());
        }

        // Update cache
        plugin.cachePlayerData(username, data);

        // Create session if enabled
        if (plugin.getConfig().getBoolean("session.enabled")) {
            plugin.getSessionManager().createSession(player);
        }

        // Send success message
        player.sendMessage(plugin.getLanguageManager().getMessage("login.success"));

        // Teleport to spawn if configured
        if (plugin.getConfig().getBoolean("spawn.teleport_after_login")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.teleport(plugin.getConfigManager().getSpawnLocation());
            });
        }

        // Reset login attempts
        plugin.getSecurityUtils().resetLoginAttempts(player);
    }

    private void handleFailedLogin(Player player) {
        plugin.getSecurityUtils().recordFailedAttempt(player);
        player.sendMessage(plugin.getLanguageManager().getMessage("login.wrong_password"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>(); // No tab completion for security
    }
}
