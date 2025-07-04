// dev/doom/customauth/models/PlayerData.java
package dev.doom.customauth.models;

import java.time.Instant;

public class PlayerData {
    private final String username;
    private String hashedPassword;
    private String email;
    private String lastIp;
    private long lastLogin;
    private boolean isLoggedIn;
    private boolean isVerified;
    private String verificationToken;
    private long verificationExpiry;
    private int loginAttempts;
    private long registrationDate;
    
    // Bedrock-specific fields
    private boolean isBedrockPlayer;
    private String xuid;
    private String deviceId;
    private String deviceOs;
    private String languageCode;

    public PlayerData(String username, String hashedPassword) {
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.isLoggedIn = false;
        this.isVerified = false;
        this.loginAttempts = 0;
        this.registrationDate = Instant.now().getEpochSecond();
    }

    // Getters
    public String getUsername() { return username; }
    public String getHashedPassword() { return hashedPassword; }
    public String getEmail() { return email; }
    public String getLastIp() { return lastIp; }
    public long getLastLogin() { return lastLogin; }
    public boolean isLoggedIn() { return isLoggedIn; }
    public boolean isVerified() { return isVerified; }
    public String getVerificationToken() { return verificationToken; }
    public long getVerificationExpiry() { return verificationExpiry; }
    public int getLoginAttempts() { return loginAttempts; }
    public long getRegistrationDate() { return registrationDate; }
    public boolean isBedrockPlayer() { return isBedrockPlayer; }
    public String getXuid() { return xuid; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceOs() { return deviceOs; }
    public String getLanguageCode() { return languageCode; }

    // Setters
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }
    public void setEmail(String email) { this.email = email; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }
    public void setVerified(boolean verified) { isVerified = verified; }
    public void setVerificationToken(String token) { this.verificationToken = token; }
    public void setVerificationExpiry(long expiry) { this.verificationExpiry = expiry; }
    public void setBedrockPlayer(boolean bedrockPlayer) { isBedrockPlayer = bedrockPlayer; }
    public void setXuid(String xuid) { this.xuid = xuid; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setDeviceOs(String deviceOs) { this.deviceOs = deviceOs; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }

    // Utility methods
    public void incrementLoginAttempts() { this.loginAttempts++; }
    public void resetLoginAttempts() { this.loginAttempts = 0; }
    
    public boolean isVerificationExpired() {
        return verificationToken != null && 
               Instant.now().getEpochSecond() > verificationExpiry;
    }

    public boolean isSameDevice(String deviceId, String deviceOs) {
        return this.deviceId != null && 
               this.deviceOs != null && 
               this.deviceId.equals(deviceId) && 
               this.deviceOs.equals(deviceOs);
    }

    public void updateLastAccess() {
        this.lastLogin = Instant.now().getEpochSecond();
    }
}
