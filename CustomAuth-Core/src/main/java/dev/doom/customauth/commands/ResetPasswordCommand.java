// dev/doom/customauth/commands/ResetPasswordCommand.java
package dev.doom.customauth.commands;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetPasswordCommand implements CommandExecutor {
    private final CustomAuth plugin;

    public ResetPasswordCommand(CustomAuth plugin) {
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
            player.sendMessage(plugin.getLanguageManager().getMessage("bedrock.no_password_reset"));
            return true;
        }

        String username = player.getName().toLowerCase();
        PlayerData data = plugin.getCachedPlayerData(username);

        if (data == null || data.getEmail() == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("resetpassword.no_email"));
            return true;
        }

        // Generate reset token
        String resetToken = plugin.getSecurityUtils().generateToken();
        
        // Store reset token
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().saveResetToken(username, resetToken, 
                System.currentTimeMillis() + (3600 * 1000)); // 1 hour expiry
        }

        // Send reset email
        plugin.getEmailSender().sendPasswordResetEmail(username, data.getEmail(), resetToken)
            .thenAccept(sent -> {
                if (sent) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("resetpassword.email_sent"));
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("error.email_failed"));
                }
            });

        return true;
    }
}
