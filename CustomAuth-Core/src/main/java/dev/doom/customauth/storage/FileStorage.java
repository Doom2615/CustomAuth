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
    private final int batchSize;
    private final long saveInterval;

    public FileStorage(CustomAuth plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.playerData = new ConcurrentHashMap<>();
        this.saveQueue = new ConcurrentLinkedQueue<>();
        this.batchSize = plugin.getConfig().getInt("storage.file.batch-size", 100);
        this.saveInterval = plugin.getConfig().getLong("storage.file.save-interval", 300);

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        startBatchProcessor();
    }

    private void startBatchProcessor() {
        plugin.scheduleTask(() -> {
            List<PlayerData> batch = new ArrayList<>();
            PlayerData data;
            while ((data = saveQueue.poll()) != null && batch.size() < batchSize) {
                batch.add(data);
            }
            if (!batch.isEmpty()) {
                saveBatch(batch);
            }
        }, 20L * saveInterval, 20L * saveInterval);
    }

    private void saveBatch(List<PlayerData> batch) {
        for (PlayerData data : batch) {
            try {
                File playerFile = new File(dataFolder, data.getUsername().toLowerCase() + ".yml");
                YamlConfiguration config = new YamlConfiguration();

                // Basic data
                config.set("username", data.getUsername());
                config.set("password", data.getHashedPassword());
                config.set("email", data.getEmail());
                config.set("lastIp", data.getLastIp());
                config.set("lastLogin", data.getLastLogin());
                config.set("verified", data.isVerified());

                // Bedrock data
                config.set("bedrock.isBedrockPlayer", data.isBedrockPlayer());
                config.set("bedrock.xuid", data.getXuid());
                config.set("bedrock.deviceId", data.getDeviceId());
                config.set("bedrock.deviceOs", data.getDeviceOs());

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

            // Load basic data
            data.setEmail(config.getString("email"));
            data.setLastIp(config.getString("lastIp"));
            data.setLastLogin(config.getLong("lastLogin"));
            data.setVerified(config.getBoolean("verified"));

            // Load Bedrock data
            data.setBedrockPlayer(config.getBoolean("bedrock.isBedrockPlayer"));
            data.setXuid(config.getString("bedrock.xuid"));
            data.setDeviceId(config.getString("bedrock.deviceId"));
            data.setDeviceOs(config.getString("bedrock.deviceOs"));

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
// dev/doom/customauth/storage/FileStorage.java (continued)

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
                File playerFile = new File(dataFolder, entry.getKey() + ".yml");
                entry.getValue().save(playerFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save player data for " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    public boolean isRegistered(String username) {
        return new File(dataFolder, username.toLowerCase() + ".yml").exists();
    }

    public void updateLoginData(String username, String ip, long timestamp) {
        PlayerData data = loadPlayer(username);
        if (data != null) {
            data.setLastIp(ip);
            data.setLastLogin(timestamp);
            queueSave(data);
        }
    }

    public void updateBedrockData(String username, String deviceId, String deviceOs) {
        PlayerData data = loadPlayer(username);
        if (data != null && data.isBedrockPlayer()) {
            data.setDeviceId(deviceId);
            data.setDeviceOs(deviceOs);
            queueSave(data);
        }
    }

    public void clearCache() {
        playerData.clear();
    }

    public int getCacheSize() {
        return playerData.size();
    }

    public void backup() {
        File backupFolder = new File(plugin.getDataFolder(), "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        File backupDir = new File(backupFolder, "backup-" + timestamp);
        backupDir.mkdirs();

        for (File playerFile : dataFolder.listFiles((dir, name) -> name.endsWith(".yml"))) {
            try {
                File backupFile = new File(backupDir, playerFile.getName());
                java.nio.file.Files.copy(playerFile.toPath(), backupFile.toPath());
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to backup player file " + playerFile.getName() + ": " + e.getMessage());
            }
        }

        // Clean old backups (keep last 5)
        File[] backups = backupFolder.listFiles((dir, name) -> name.startsWith("backup-"));
        if (backups != null && backups.length > 5) {
            java.util.Arrays.sort(backups, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            for (int i = 5; i < backups.length; i++) {
                deleteDirectory(backups[i]);
            }
        }
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    public void importLegacyData() {
        File legacyFolder = new File(plugin.getDataFolder(), "legacy");
        if (!legacyFolder.exists()) return;

        File[] legacyFiles = legacyFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (legacyFiles == null) return;

        for (File legacyFile : legacyFiles) {
            try {
                YamlConfiguration legacyConfig = YamlConfiguration.loadConfiguration(legacyFile);
                PlayerData data = new PlayerData(
                    legacyConfig.getString("username"),
                    legacyConfig.getString("password")
                );
                
                // Convert legacy data format to new format
                data.setEmail(legacyConfig.getString("email", ""));
                data.setLastIp(legacyConfig.getString("ip", ""));
                data.setLastLogin(legacyConfig.getLong("lastLogin", 0));
                data.setVerified(legacyConfig.getBoolean("verified", false));
                
                queueSave(data);
                
                // Archive legacy file
                File archiveFolder = new File(legacyFolder, "archived");
                archiveFolder.mkdirs();
                File archiveFile = new File(archiveFolder, legacyFile.getName());
                legacyFile.renameTo(archiveFile);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to import legacy data from " + legacyFile.getName() + ": " + e.getMessage());
            }
        }
    }

    public CompletableFuture<List<String>> searchPlayers(String pattern) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> matches = new ArrayList<>();
            File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            
            if (files != null) {
                for (File file : files) {
                    String username = file.getName().replace(".yml", "");
                    if (username.toLowerCase().contains(pattern.toLowerCase())) {
                        matches.add(username);
                    }
                }
            }
            
            return matches;
        }, plugin.getAsyncExecutor());
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        long inactiveThreshold = plugin.getConfig().getLong("storage.file.inactive-days", 90) * 24 * 60 * 60 * 1000;

        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                long lastLogin = config.getLong("lastLogin", 0);
                
                if (now - lastLogin > inactiveThreshold) {
                    // Archive inactive player data
                    File archiveFolder = new File(plugin.getDataFolder(), "inactive");
                    archiveFolder.mkdirs();
                    File archiveFile = new File(archiveFolder, file.getName());
                    file.renameTo(archiveFile);
                    
                    // Remove from cache
                    playerData.remove(file.getName().replace(".yml", ""));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to process cleanup for " + file.getName() + ": " + e.getMessage());
            }
        }
    }
}
