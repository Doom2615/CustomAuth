// dev/doom/customauth/commands/AdminCommand.java
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
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {
    private final CustomAuth plugin;

    public AdminCommand(CustomAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customauth.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("error.no_permission"));
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "unregister" -> handleUnregister(sender, args);
            case "forcelogin" -> handleForceLogin(sender, args);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "cleanup" -> handleCleanup(sender);
            case "reset2fa" -> handleReset2FA(sender, args);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleUnregister(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.unregister_usage"));
            return;
        }

        String targetUsername = args[1].toLowerCase();
        
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().deletePlayer(targetUsername).thenAccept(success -> {
                if (success) {
                    handleSuccessfulUnregister(sender, targetUsername);
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("admin.unregister_failed"));
                }
            });
        } else {
            plugin.getFileStorage().deletePlayer(targetUsername);
            handleSuccessfulUnregister(sender, targetUsername);
        }
    }

    private void handleSuccessfulUnregister(CommandSender sender, String username) {
        // Remove from cache
        plugin.getPlayerCache().invalidate(username);
        
        // Remove sessions
        plugin.getSessionManager().invalidateAllSessions(username);
        
        // Kick player if online
        Player target = plugin.getServer().getPlayer(username);
        if (target != null) {
            if (plugin.isFolia()) {
                target.getScheduler().run(plugin, task -> 
                    target.kickPlayer(plugin.getLanguageManager().getMessage("admin.unregistered")), () -> {});
            } else {
                target.kickPlayer(plugin.getLanguageManager().getMessage("admin.unregistered"));
            }
        }

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.unregister_success")
            .replace("%player%", username));
    }

    private void handleForceLogin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.forcelogin_usage"));
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("error.player_not_found"));
            return;
        }

        PlayerData data = plugin.getCachedPlayerData(target.getName().toLowerCase());
        if (data == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("error.player_not_registered"));
            return;
        }

        data.setLoggedIn(true);
        plugin.cachePlayerData(target.getName().toLowerCase(), data);
        target.sendMessage(plugin.getLanguageManager().getMessage("admin.forced_login"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.forcelogin_success")
            .replace("%player%", target.getName()));
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadConfigs();
        plugin.getLanguageManager().reloadMessages();
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.reload_success"));
    }

    private void handleStatus(CommandSender sender) {
        int totalPlayers = plugin.getServer().getOnlinePlayers().size();
        int authenticatedPlayers = (int) plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> {
                PlayerData data = plugin.getCachedPlayerData(p.getName().toLowerCase());
                return data != null && data.isLoggedIn();
            })
            .count();
        int bedrockPlayers = plugin.getBedrockAuthHandler() != null ? 
            plugin.getBedrockAuthHandler().getActiveBedrockPlayers() : 0;

        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.status_header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.status_online")
            .replace("%count%", String.valueOf(totalPlayers)));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.status_authenticated")
            .replace("%count%", String.valueOf(authenticatedPlayers)));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.status_bedrock")
            .replace("%count%", String.valueOf(bedrockPlayers)));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.status_storage")
            .replace("%type%", plugin.getDatabase() != null ? "MySQL" : "File"));
    }

    private void handleCleanup(CommandSender sender) {
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().cleanup().thenRun(() -> 
                sender.sendMessage(plugin.getLanguageManager().getMessage("admin.cleanup_success")));
        } else {
            plugin.getFileStorage().cleanup();
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.cleanup_success"));
        }
    }

    private void handleReset2FA(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.reset2fa_usage"));
            return;
        }

        String targetUsername = args[1].toLowerCase();
        PlayerData data = plugin.getCachedPlayerData(targetUsername);
        
        if (data == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("error.player_not_found"));
            return;
        }

        data.setVerified(false);
        if (plugin.getDatabase() != null) {
            plugin.getDatabase().reset2FA(targetUsername).thenAccept(success -> {
                if (success) {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("admin.reset2fa_success")
                        .replace("%player%", targetUsername));
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("admin.reset2fa_failed"));
                }
            });
        } else {
            plugin.getFileStorage().queueSave(data);
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.reset2fa_success")
                .replace("%player%", targetUsername));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_usage"));
            return;
        }

        String targetUsername = args[1].toLowerCase();
        PlayerData data = plugin.getCachedPlayerData(targetUsername);
        
        if (data == null) {
            if (plugin.getDatabase() != null) {
                plugin.getDatabase().getPlayerData(targetUsername).thenAccept(optionalData -> {
                    optionalData.ifPresentOrElse(
                        playerData -> sendPlayerInfo(sender, playerData),
                        () -> sender.sendMessage(plugin.getLanguageManager().getMessage("error.player_not_found"))
                    );
                });
            } else {
                data = plugin.getFileStorage().loadPlayer(targetUsername);
                if (data != null) {
                    sendPlayerInfo(sender, data);
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("error.player_not_found"));
                }
            }
        } else {
            sendPlayerInfo(sender, data);
        }
    }
    private void sendPlayerInfo(CommandSender sender, PlayerData data) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_header")
            .replace("%player%", data.getUsername()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_registered")
            .replace("%date%", new java.util.Date(data.getLastLogin()).toString()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_last_login")
            .replace("%date%", new java.util.Date(data.getLastLogin()).toString()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_last_ip")
            .replace("%ip%", data.getLastIp()));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_email")
            .replace("%email%", data.getEmail() != null ? data.getEmail() : "Not set"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_verified")
            .replace("%verified%", data.isVerified() ? "Yes" : "No"));
        
        if (data.isBedrockPlayer()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_bedrock_header"));
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_bedrock_xuid")
                .replace("%xuid%", data.getXuid()));
            sender.sendMessage(plugin.getLanguageManager().getMessage("admin.info_bedrock_device")
                .replace("%device%", data.getDeviceOs()));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_header"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_unregister"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_forcelogin"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_reload"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_status"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_cleanup"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_reset2fa"));
        sender.sendMessage(plugin.getLanguageManager().getMessage("admin.help_info"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("customauth.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("unregister");
            completions.add("forcelogin");
            completions.add("reload");
            completions.add("status");
            completions.add("cleanup");
            completions.add("reset2fa");
            completions.add("info");
            
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "unregister":
                case "forcelogin":
                case "reset2fa":
                case "info":
                    return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
