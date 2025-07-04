// dev/doom/customauth/commands/LogoutCommand.java
package dev.doom.customauth.commands;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LogoutCommand implements CommandExecutor {
    private final CustomAuth plugin;

    public LogoutCommand(CustomAuth plugin) {
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
            player.sendMessage(plugin.getLanguageManager().getMessage("bedrock.no_logout"));
            return true;
        }

        String username = player.getName().toLowerCase();
        PlayerData data = plugin.getCachedPlayerData(username);

        if (data == null || !data.isLoggedIn()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("logout.not_logged_in"));
            return true;
        }

        // Perform logout
        data.setLoggedIn(false);
        plugin.cachePlayerData(username, data);

        // Remove session
        plugin.getSessionManager().removeSession(username);

        // Teleport to spawn if configured
        if (plugin.getConfig().getBoolean("spawn.teleport_after_logout")) {
            if (plugin.isFolia()) {
                player.getScheduler().run(plugin, task -> 
                    player.teleport(plugin.getConfigManager().getSpawnLocation()), () -> {});
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.teleport(plugin.getConfigManager().getSpawnLocation()));
            }
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("logout.success"));
        return true;
    }
}
