// dev/doom/customauth/models/PlayerData.java
package dev.doom.customauth.models;

public class PlayerData {
    private final String username;
    private String hashedPassword;
    private String email;
    private String lastIp;
    private long lastLogin;
    private boolean isLoggedIn;
    private boolean isVerified;
    
    // Bedrock-specific fields
    private boolean isBedrockPlayer;
    private String xuid;
    private String deviceId;
    private String deviceOs;

    public PlayerData(String username, String hashedPassword) {
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.isLoggedIn = false;
        this.isVerified = false;
        this.isBedrockPlayer = false;
    }

    // Getters and setters
    public String getUsername() { return username; }
    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }
    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    public boolean isLoggedIn() { return isLoggedIn; }
    public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }
    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }
    public boolean isBedrockPlayer() { return isBedrockPlayer; }
    public void setBedrockPlayer(boolean bedrockPlayer) { isBedrockPlayer = bedrockPlayer; }
    public String getXuid() { return xuid; }
    public void setXuid(String xuid) { this.xuid = xuid; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceOs() { return deviceOs; }
    public void setDeviceOs(String deviceOs) { this.deviceOs = deviceOs; }
}
