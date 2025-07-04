// dev/doom/customauth/models/SessionData.java
package dev.doom.customauth.models;

import java.util.UUID;

public record SessionData(
    String username,
    String token,
    long expiry,
    String ip,
    UUID uuid,
    long createdAt
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiry;
    }

    public long getRemainingTime() {
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    public SessionData withNewExpiry(long newExpiry) {
        return new SessionData(username, token, newExpiry, ip, uuid, createdAt);
    }
}
