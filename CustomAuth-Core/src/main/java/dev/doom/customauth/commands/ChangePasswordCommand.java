// dev/doom/customauth/commands/ChangePasswordCommand.java
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

public class ChangePasswordCommand implements CommandExecutor, TabCompleter {
    private final CustomAuth plugin;

    public ChangePasswordCommand(CustomAuth plugin) {
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
            player.sendMessage(plugin.getLanguageManager().getMessage("bedrock.no_password_change"));
            return true;
        }

        // Schedule password change process
        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> handlePasswordChange(player, args), () -> {});
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, 
                () -> handlePasswordChange(player, args));
        }

        return true;
    }

    private void handlePasswordChange(Player player, String[] args) {
        String username = player.getName().toLowerCase();

        // Check if logged in
        PlayerData data = plugin.getCachedPlayerData(username);
        if (data == null || !data.isLoggedIn()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("error.not_logged_in"));
            return;
        }

        // Validate arguments
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("changepassword.usage"));
            return;
        }

        String oldPassword = args[0];
        String newPassword = args[1];

        // Verify old password
        if (!plugin.getSecurityUtils().checkPassword(oldPassword, data.getHashedPassword())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("changepassword.wrong_password"));
            return;
        }

        // Validate new password
        if (!plugin.getSecurityUtils().isPasswordValid(newPassword)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("changepassword.password_too_weak"));
            return;
        }

        // Update password
        String newHash = plugin.getSecurityUtils().hashPassword(newPassword);
        data.setHashedPassword(newHash);

        // Update storage
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().updatePassword(username, newHash).thenAccept(success -> {
                if (success) {
                    handleSuccessfulPasswordChange(player, data);
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("error.password_change_failed"));
                }
            });
        } else {
            plugin.getFileStorage().queueSave(data);
            handleSuccessfulPasswordChange(player, data);
        }
    }

    private void handleSuccessfulPasswordChange(Player player, PlayerData data) {
        // Update cache
        plugin.cachePlayerData(player.getName().toLowerCase(), data);

        // Invalidate all sessions if configured
        if (plugin.getConfig().getBoolean("security.invalidate_sessions_on_password_change")) {
            plugin.getSessionManager().invalidateAllSessions(player.getName().toLowerCase());
        }

        // Send success message
        player.sendMessage(plugin.getLanguageManager().getMessage("changepassword.success"));

        // Send email notification if enabled
        if (plugin.getEmailSender() != null && data.getEmail() != null) {
            plugin.getEmailSender().sendPasswordChangeNotification(
                player.getName(), data.getEmail());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>(); // No tab completion for security
    }
}
