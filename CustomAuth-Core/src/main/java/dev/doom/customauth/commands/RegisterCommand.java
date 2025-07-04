// dev/doom/customauth/commands/RegisterCommand.java
package dev.doom.customauth.commands;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {
    private final CustomAuth plugin;

    public RegisterCommand(CustomAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error.console_command"));
            return true;
        }

        // Skip if player is Bedrock
        if (plugin.getBedrockAuthHandler() != null && 
            plugin.getBedrockAuthHandler().isBedrockPlayer(player)) {
            player.sendMessage(plugin.getConfigManager().getMessage("bedrock.already_registered"));
            return true;
        }

        // Schedule registration process
        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> handleRegistration(player, args), () -> {});
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, 
                () -> handleRegistration(player, args));
        }

        return true;
    }

    private void handleRegistration(Player player, String[] args) {
        String username = player.getName().toLowerCase();

        // Check if already registered
        if (plugin.getDatabase() != null ? 
            plugin.getDatabase().isRegistered(username) :
            plugin.getFileStorage().loadPlayer(username) != null) {
            player.sendMessage(plugin.getConfigManager().getMessage("register.already_registered"));
            return;
        }

        // Validate arguments
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("register.usage"));
            return;
        }

        if (!args[0].equals(args[1])) {
            player.sendMessage(plugin.getConfigManager().getMessage("register.passwords_not_match"));
            return;
        }

        if (!plugin.getSecurityManager().isPasswordValid(args[0])) {
            player.sendMessage(plugin.getConfigManager().getMessage("register.password_too_weak"));
            return;
        }

        // Handle email if required
        String email = args.length > 2 ? args[2] : null;
        if (plugin.getConfig().getBoolean("email.required") && email == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("register.email_required"));
            return;
        }

        // Register the player
        String hashedPassword = plugin.getSecurityManager().hashPassword(args[0]);
        PlayerData data = new PlayerData(username, hashedPassword);
        data.setEmail(email);
        data.setLastIp(player.getAddress().getAddress().getHostAddress());
        data.setLastLogin(System.currentTimeMillis());

        if (plugin.getDatabase() != null) {
            plugin.getDatabase().registerPlayer(username, hashedPassword, email)
                .thenAccept(success -> {
                    if (success) {
                        handleSuccessfulRegistration(player, data, email);
                    } else {
                        player.sendMessage(plugin.getConfigManager().getMessage("error.registration_failed"));
                    }
                });
        } else {
            plugin.getFileStorage().queueSave(data);
            handleSuccessfulRegistration(player, data, email);
        }
    }

    private void handleSuccessfulRegistration(Player player, PlayerData data, String email) {
        plugin.cachePlayerData(player.getName().toLowerCase(), data);
        plugin.getMetrics().incrementRegistrations();
        
        player.sendMessage(plugin.getConfigManager().getMessage("register.success"));

        // Handle email verification if enabled
        if (email != null && plugin.getEmailVerification() != null) {
            plugin.getEmailVerification().sendVerificationEmail(player.getName(), email)
                .thenAccept(sent -> {
                    if (sent) {
                        player.sendMessage(plugin.getConfigManager().getMessage("email.verification_sent"));
                    }
                });
        }
    }
}
