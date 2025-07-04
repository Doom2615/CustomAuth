// dev/doom/customauth/handlers/PlayerProtectionHandler.java
package dev.doom.customauth.handlers;

import dev.doom.customauth.CustomAuth;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;

public class PlayerProtectionHandler implements Listener {
    private final CustomAuth plugin;

    public PlayerProtectionHandler(CustomAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            if (event.getTo().getBlockX() != event.getFrom().getBlockX() ||
                event.getTo().getBlockZ() != event.getFrom().getBlockZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("security.must_login"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            String command = event.getMessage().split(" ")[0].toLowerCase();
            if (!isAllowedCommand(command)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("security.must_login"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player &&
            !isAuthenticated((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player &&
            !isAuthenticated((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player &&
            !isAuthenticated((Player) event.getTarget())) {
            event.setCancelled(true);
        }
    }

    private boolean isAuthenticated(Player player) {
        return plugin.getCachedPlayerData(player.getName().toLowerCase()) != null &&
               plugin.getCachedPlayerData(player.getName().toLowerCase()).isLoggedIn();
    }

    private boolean isAllowedCommand(String command) {
        return command.equals("/login") ||
               command.equals("/register") ||
               command.equals("/l") ||
               command.equals("/reg");
    }
}
