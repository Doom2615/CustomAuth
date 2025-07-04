// dev/doom/customauth/utils/PasswordValidator.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

public class PasswordValidator {
    private final Pattern passwordPattern;
    private final int minLength;
    private final Set<String> bannedPasswords;

    public PasswordValidator(CustomAuth plugin) {
        this.passwordPattern = Pattern.compile(
            plugin.getConfig().getString("security.password_regex",
            "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$")
        );
        this.minLength = plugin.getConfig().getInt("security.min_password_length", 8);
        this.bannedPasswords = new HashSet<>(plugin.getConfig().getStringList("security.banned_passwords"));
    }

    public boolean isValid(String password) {
        if (password == null || password.length() < minLength) {
            return false;
        }
        
        if (bannedPasswords.contains(password.toLowerCase())) {
            return false;
        }
        
        return passwordPattern.matcher(password).matches();
    }

    public String getRequirements() {
        return String.format(
            "Password must:\n" +
            "- Be at least %d characters long\n" +
            "- Contain letters and numbers\n" +
            "- Contain special characters\n" +
            "- Not be a common password",
            minLength
        );
    }
}
