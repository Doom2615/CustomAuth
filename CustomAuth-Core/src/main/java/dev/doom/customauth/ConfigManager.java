// ConfigManager.java
package dev.doom.customauth;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final CustomAuth plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration emails;
    private final Map<String, String> messageCache = new HashMap<>();

    public ConfigManager(CustomAuth plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        // Main config
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Messages config
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Email templates
        File emailsFile = new File(plugin.getDataFolder(), "emails.yml");
        if (!emailsFile.exists()) {
            plugin.saveResource("emails.yml", false);
        }
        emails = YamlConfiguration.loadConfiguration(emailsFile);

        // Clear cache
        messageCache.clear();
    }

    public String getMessage(String path) {
        return messageCache.computeIfAbsent(path, key -> {
            String message = messages.getString(path);
            if (message == null) {
                return "Missing message: " + path;
            }
            String prefix = messages.getString("prefix", "");
            return colorize(prefix + message);
        });
    }

    public String getEmailTemplate() {
        return emails.getString("verification_email");
    }

    private String colorize(String message) {
        return message.replace('&', '§');
    }

    public void reloadConfigs() {
        loadConfigs();
    }
}
