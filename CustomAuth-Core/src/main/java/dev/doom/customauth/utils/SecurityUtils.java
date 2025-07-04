// dev/doom/customauth/utils/SecurityUtils.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.net.InetAddress;
import com.google.common.util.concurrent.RateLimiter;

public class SecurityUtils {
    private final CustomAuth plugin;
    private final Pattern passwordPattern;
    private final Pattern emailPattern;
    private final SecureRandom secureRandom;
    private final Map<String, Integer> loginAttempts;
    private final Map<String, Long> ipBans;
    private final Map<String, RateLimiter> ipRateLimiters;

    public SecurityUtils(CustomAuth plugin) {
        this.plugin = plugin;
        this.passwordPattern = Pattern.compile(
            plugin.getConfig().getString("security.password_regex",
            "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$")
        );
        this.emailPattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@(.+)$"
        );
        this.secureRandom = new SecureRandom();
        this.loginAttempts = new ConcurrentHashMap<>();
        this.ipBans = new ConcurrentHashMap<>();
        this.ipRateLimiters = new ConcurrentHashMap<>();
    }

    public boolean isPasswordValid(String password) {
        if (password == null) return false;

        int minLength = plugin.getConfig().getInt("security.min_password_length", 8);
        int maxLength = plugin.getConfig().getInt("security.max_password_length", 32);

        if (password.length() < minLength || password.length() > maxLength) {
            return false;
        }

        if (plugin.getConfig().getBoolean("security.require_special_char") && 
            !password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
            return false;
        }

        if (plugin.getConfig().getBoolean("security.require_number") && 
            !password.matches(".*\\d.*")) {
            return false;
        }

        if (plugin.getConfig().getBoolean("security.require_uppercase") && 
            !password.matches(".*[A-Z].*")) {
            return false;
        }

        return passwordPattern.matcher(password).matches();
    }

    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public boolean checkPassword(String password, String hashedPassword) {
        try {
            return BCrypt.checkpw(password, hashedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidEmail(String email) {
        return email != null && emailPattern.matcher(email).matches();
    }

    public String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public boolean isIpAllowed(InetAddress address) {
        String ip = address.getHostAddress();
        
        // Check IP ban
        Long banExpiry = ipBans.get(ip);
        if (banExpiry != null) {
            if (System.currentTimeMillis() > banExpiry) {
                ipBans.remove(ip);
                loginAttempts.remove(ip);
                return true;
            }
            return false;
        }

        // Check rate limit
        RateLimiter rateLimiter = ipRateLimiters.computeIfAbsent(ip,
            k -> RateLimiter.create(plugin.getConfig().getDouble("security.ip_rate_limit", 5.0)));
        
        return rateLimiter.tryAcquire();
    }

    public boolean canAttemptLogin(Player player) {
        String ip = player.getAddress().getAddress().getHostAddress();
        int attempts = loginAttempts.getOrDefault(ip, 0);
        return attempts < plugin.getConfig().getInt("security.max_login_attempts", 3);
    }

    public void recordFailedAttempt(Player player) {
        String ip = player.getAddress().getAddress().getHostAddress();
        int attempts = loginAttempts.compute(ip, (k, v) -> v == null ? 1 : v + 1);

        if (attempts >= plugin.getConfig().getInt("security.max_login_attempts", 3)) {
            long banDuration = plugin.getConfig().getLong("security.ip_ban_duration", 3600) * 1000;
            ipBans.put(ip, System.currentTimeMillis() + banDuration);
        }
    }

    public void resetLoginAttempts(Player player) {
        loginAttempts.remove(player.getAddress().getAddress().getHostAddress());
    }

    public void cleanupExpiredBans() {
        long now = System.currentTimeMillis();
        ipBans.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public String generateSessionId() {
        return generateToken();
    }

    public String maskIp(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return ip;
    }
    public String generateSecureToken(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedInput = input + System.currentTimeMillis() + secureRandom.nextLong();
            byte[] hash = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Failed to generate secure token: " + e.getMessage());
            // Fallback to a simple but still secure token
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }
    }

    public String generateSessionToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String generateVerificationToken() {
        return generateSecureToken("verify");
    }

    public String generateResetToken() {
        return generateSecureToken("reset");
    }
}
