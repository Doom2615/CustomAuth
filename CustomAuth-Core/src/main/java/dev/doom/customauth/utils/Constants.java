// dev/doom/customauth/utils/Constants.java
package dev.doom.customauth.utils;

public class Constants {
    public static final String PLUGIN_VERSION = "1.0.0";
    public static final int CONFIG_VERSION = 1;
    public static final int RESOURCE_ID = 12345; // Your plugin's resource ID

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 32;
    public static final int MAX_LOGIN_ATTEMPTS = 3;
    public static final int MAX_IP_ATTEMPTS = 5;
    public static final long DEFAULT_SESSION_TIMEOUT = 7200; // 2 hours in seconds
    public static final long DEFAULT_IP_BAN_DURATION = 3600; // 1 hour in seconds

    public static final String DEFAULT_LANGUAGE = "en";
    public static final String DATABASE_VERSION = "1.0";
    
    private Constants() {
        // Prevent instantiation
    }
}
