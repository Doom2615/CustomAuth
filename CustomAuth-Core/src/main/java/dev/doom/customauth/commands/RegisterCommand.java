// dev/doom/customauth/commands/RegisterCommand.java
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

public class RegisterCommand implements CommandExecutor, TabCompleter {
    private final CustomAuth plugin;

    public RegisterCommand(CustomAuth plugin) {
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
            player.sendMessage(plugin.getLanguageManager().getMessage("bedrock.already_registered"));
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
            plugin.getDatabase().isRegistered(username).join() :
            plugin.getFileStorage().isRegistered(username)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("register.already_registered"));
            return;
        }

        // Validate arguments
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("register.usage"));
            return;
        }

        if (!args[0].equals(args[1])) {
            player.sendMessage(plugin.getLanguageManager().getMessage("register.passwords_not_match"));
            return;
        }

        // Validate password
        if (!plugin.getSecurityUtils().isPasswordValid(args[0])) {
            player.sendMessage(plugin.getLanguageManager().getMessage("register.password_too_weak"));
            return;
        }

        // Handle email if required
        String email = args.length > 2 ? args[2] : null;
        if (plugin.getConfig().getBoolean("email.required") && email == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("register.email_required"));
            return;
        }

        // Validate email if provided
        if (email != null && !plugin.getSecurityUtils().isValidEmail(email)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("register.invalid_email"));
            return;
        }

        // Register the player
        String hashedPassword = plugin.getSecurityUtils().hashPassword(args[0]);
        PlayerData data = new PlayerData(username, hashedPassword);
        data.setEmail(email);
        data.setLastIp(player.getAddress().getAddress().getHostAddress());
        data.setLastLogin(System.currentTimeMillis());

        boolean success = false;
        if (plugin.getDatabase() != null) {
            success = plugin.getDatabase().registerPlayer(username, hashedPassword, email).join();
        } else {
            plugin.getFileStorage().queueSave(data);
            success = true;
        }

        if (success) {
            handleSuccessfulRegistration(player, data, email);
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("error.registration_failed"));
        }
    }

    private void handleSuccessfulRegistration(Player player, PlayerData data, String email) {
        plugin.cachePlayerData(player.getName().toLowerCase(), data);
        
        // Send success message
        player.sendMessage(plugin.getLanguageManager().getMessage("register.success"));

        // Handle email verification if enabled
        if (email != null && plugin.getEmailSender() != null) {
            String verificationToken = plugin.getSecurityUtils().generateToken();
            plugin.getEmailSender().sendVerificationEmail(player.getName(), email, verificationToken)
                .thenAccept(sent -> {
                    if (sent) {
                        player.sendMessage(plugin.getLanguageManager().getMessage("email.verification_sent"));
                    }
                });
        }

        // Auto-login after registration if enabled
        if (plugin.getConfig().getBoolean("login.auto_login_after_register")) {
            data.setLoggedIn(true);
            player.sendMessage(plugin.getLanguageManager().getMessage("login.auto_login"));
        }

        // Teleport to spawn if configured
        if (plugin.getConfig().getBoolean("spawn.teleport_after_register")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.teleport(plugin.getConfigManager().getSpawnLocation());
            });
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>(); // No tab completion for security
    }
}
