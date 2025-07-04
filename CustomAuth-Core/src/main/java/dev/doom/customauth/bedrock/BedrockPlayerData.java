// dev/doom/customauth/bedrock/BedrockPlayerData.java
package dev.doom.customauth.bedrock;

public record BedrockPlayerData(
    String username,
    String xuid,
    String deviceOs,
    String deviceId,
    String languageCode
) {
    public boolean isSameDevice(BedrockPlayerData other) {
        return deviceId.equals(other.deviceId) && 
               deviceOs.equals(other.deviceOs);
    }

    public boolean isValidXuid() {
        return xuid != null && !xuid.isEmpty() && xuid.matches("^[0-9]{16}$");
    }

    public static BedrockPlayerData createDefault(String username, String xuid) {
        return new BedrockPlayerData(
            username,
            xuid,
            "UNKNOWN",
            "UNKNOWN",
            "en_US"
        );
    }

    public BedrockPlayerData withUpdatedDevice(String newDeviceId, String newDeviceOs) {
        return new BedrockPlayerData(
            username,
            xuid,
            newDeviceOs,
            newDeviceId,
            languageCode
        );
    }

    public BedrockPlayerData withUpdatedLogin() {
        return new BedrockPlayerData(
            username,
            xuid,
            deviceOs,
            deviceId,
            languageCode
        );
    }
}
