// AdminCommand.java
package dev.doom.customauth.commands;

import dev.doom.customauth.CustomAuth;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {
    private final CustomAuth plugin;

    public AdminCommand(CustomAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customauth.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "unregister" -> handleUnregister(sender, args);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "cleanup" -> handleCleanup(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleUnregister(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /authadmin unregister <player>");
            return;
        }

        String targetPlayer = args[1].toLowerCase();
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (plugin.getDatabase().unregisterPlayer(targetPlayer)) {
                sender.sendMessage("§aPlayer successfully unregistered!");
            } else {
                sender.sendMessage("§cFailed to unregister player!");
            }
        });
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadConfigs();
        sender.sendMessage("§aConfiguration reloaded!");
    }

    private void handleStatus(CommandSender sender) {
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        int authenticatedPlayers = (int) plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> plugin.getPlayerData().containsKey(p.getName().toLowerCase()))
            .count();

        sender.sendMessage("§6CustomAuth Status:");
        sender.sendMessage("§7Online players: §f" + onlinePlayers);
        sender.sendMessage("§7Authenticated players: §f" + authenticatedPlayers);
        sender.sendMessage("§7Database type: §f" + 
            (plugin.getConfig().getBoolean("mysql.enabled") ? "MySQL" : "SQLite"));
        sender.sendMessage("§7Email verification: §f" + 
            (plugin.getConfig().getBoolean("email.enabled") ? "Enabled" : "Disabled"));
    }

    private void handleCleanup(CommandSender sender) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            plugin.getSessionManager().cleanupSessions();
            plugin.getSecurityManager().cleanupIpBans();
            sender.sendMessage("§aCleanup completed!");
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6CustomAuth Admin Commands:");
        sender.sendMessage("§f/authadmin unregister <player> §7- Unregister a player");
        sender.sendMessage("§f/authadmin reload §7- Reload configuration");
        sender.sendMessage("§f/authadmin status §7- Show plugin status");
        sender.sendMessage("§f/authadmin cleanup §7- Clean up expired sessions and bans");
    }
}
