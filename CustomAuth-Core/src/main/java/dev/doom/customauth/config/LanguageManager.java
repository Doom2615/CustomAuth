// dev/doom/customauth/config/LanguageManager.java
package dev.doom.customauth.config;

import dev.doom.customauth.CustomAuth;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final CustomAuth plugin;
    private final Map<String, String> messages;
    private File messagesFile;
    private FileConfiguration messagesConfig;

    public LanguageManager(CustomAuth plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>();
        loadMessages();
    }

    public void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        messages.clear();

        for (String key : messagesConfig.getKeys(true)) {
            if (messagesConfig.isString(key)) {
                messages.put(key, ChatColor.translateAlternateColorCodes('&', 
                    messagesConfig.getString(key)));
            }
        }
    }

    public void reloadMessages() {
        loadMessages();
    }

    public String getMessage(String key) {
        String message = messages.get(key);
        if (message == null) {
            return "Missing message: " + key;
        }
        return message;
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }
}
