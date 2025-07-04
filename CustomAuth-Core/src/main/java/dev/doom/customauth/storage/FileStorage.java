// dev/doom/customauth/storage/FileStorage.java
package dev.doom.customauth.storage;

import dev.doom.customauth.CustomAuth;
import dev.doom.customauth.models.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.List;

public class FileStorage {
    private final CustomAuth plugin;
    private final File dataFolder;
    private final Map<String, YamlConfiguration> playerData;
    private final Queue<PlayerData> saveQueue;

    public FileStorage(CustomAuth plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.playerData = new ConcurrentHashMap<>();
        this.saveQueue = new ConcurrentLinkedQueue<>();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        startBatchProcessor();
    }

    private void startBatchProcessor() {
        plugin.scheduleTask(() -> {
            List<PlayerData> batch = new ArrayList<>();
            PlayerData data;
            while ((data = saveQueue.poll()) != null && batch.size() < 100) {
                batch.add(data);
            }
            if (!batch.isEmpty()) {
                saveBatch(batch);
            }
        }, 100L, 100L); // Run every 5 seconds
    }

    private void saveBatch(List<PlayerData> batch) {
        for (PlayerData data : batch) {
            try {
                File playerFile = new File(dataFolder, data.getUsername().toLowerCase() + ".yml");
                YamlConfiguration config = new YamlConfiguration();
                
                config.set("username", data.getUsername());
                config.set("password", data.getHashedPassword());
                config.set("email", data.getEmail());
                config.set("lastIp", data.getLastIp());
                config.set("lastLogin", data.getLastLogin());
                config.set("verified", data.isVerified());
                config.set("isBedrockPlayer", data.isBedrockPlayer());
                config.set("xuid", data.getXuid());
                
                config.save(playerFile);
                playerData.put(data.getUsername().toLowerCase(), config);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save player data: " + e.getMessage());
            }
        }
    }

    public void queueSave(PlayerData data) {
        saveQueue.offer(data);
    }

    public PlayerData loadPlayer(String username) {
        try {
            File playerFile = new File(dataFolder, username.toLowerCase() + ".yml");
            if (!playerFile.exists()) {
                return null;
            }

            YamlConfiguration config = playerData.computeIfAbsent(username.toLowerCase(), 
                k -> YamlConfiguration.loadConfiguration(playerFile));
            
            PlayerData data = new PlayerData(
                config.getString("username"),
                config.getString("password")
            );
            data.setEmail(config.getString("email"));
            data.setLastIp(config.getString("lastIp"));
            data.setLastLogin(config.getLong("lastLogin"));
            data.setVerified(config.getBoolean("verified"));
            data.setBedrockPlayer(config.getBoolean("isBedrockPlayer"));
            data.setXuid(config.getString("xuid"));
            
            return data;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load player data: " + e.getMessage());
            return null;
        }
    }

    public void deletePlayer(String username) {
        File playerFile = new File(dataFolder, username.toLowerCase() + ".yml");
        playerFile.delete();
        playerData.remove(username.toLowerCase());
    }

    public void saveAll() {
        // Save any remaining queued data
        List<PlayerData> remaining = new ArrayList<>();
        PlayerData data;
        while ((data = saveQueue.poll()) != null) {
            remaining.add(data);
        }
        if (!remaining.isEmpty()) {
            saveBatch(remaining);
        }

        // Save all cached data
        for (Map.Entry<String, YamlConfiguration> entry : playerData.entrySet()) {
            try {
                entry.getValue().save(new File(dataFolder, entry.getKey() + ".yml"));
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save player data: " + e.getMessage());
            }
        }
    }
        }
