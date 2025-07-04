// dev/doom/customauth/models/AuthResult.java
package dev.doom.customauth.models;

public record AuthResult(
    boolean success,
    String message,
    PlayerData playerData
) {}
