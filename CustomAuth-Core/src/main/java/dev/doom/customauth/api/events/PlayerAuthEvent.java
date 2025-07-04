// dev/doom/customauth/api/events/PlayerAuthEvent.java
package dev.doom.customauth.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerAuthEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final AuthType type;
    private boolean cancelled;

    public PlayerAuthEvent(Player player, AuthType type) {
        this.player = player;
        this.type = type;
    }

    public Player getPlayer() {
        return player;
    }

    public AuthType getType() {
        return type;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public enum AuthType {
        LOGIN,
        REGISTER,
        LOGOUT,
        SESSION_LOGIN
    }
}
