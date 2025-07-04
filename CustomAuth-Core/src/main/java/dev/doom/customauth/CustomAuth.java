package dev.doom.customauth;

import dev.doom.customauth.events.AuthListener;
import dev.doom.customauth.events.PlayerProtectionHandler;
import dev.doom.customauth.commands.*;
import dev.doom.customauth.utils.DebugLogger;
import dev.doom.customauth.api.CustomAuthAPI;
import dev.doom.customauth.bedrock.BedrockAuthHandler;
import dev.doom.customauth.config.ConfigManager;
import dev.doom.customauth.config.LanguageManager;
import dev.doom.customauth.models.PlayerData;
import dev.doom.customauth.session.SessionManager;
import dev.doom.customauth.storage.Database;
import dev.doom.customauth.storage.FileStorage;
import dev.doom.customauth.utils.EmailSender;
import dev.doom.customauth.utils.SecurityUtils;
import org.bukkit.plugin.java.JavaPlugin;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.*;
import java.time.Duration;

public class CustomAuth extends JavaPlugin {
    private boolean isFolia;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private Database database;
    private FileStorage fileStorage;
    private SessionManager sessionManager;
    private BedrockAuthHandler bedrockAuthHandler;
    private EmailSender emailSender;
    private SecurityUtils securityUtils;
    private DebugLogger debugLogger;
    
    private final Cache<String, PlayerData> playerCache;
    private final ExecutorService asyncExecutor;
    
    public CustomAuth() {
        this.playerCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
            
        this.asyncExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
    }

    @Override
    public void onEnable() {
        // Check for Folia
        this.isFolia = checkFolia();

        // Initialize configurations
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.securityUtils = new SecurityUtils(this);
        this.debugLogger = new DebugLogger(this);

        // Initialize storage
        if (getConfig().getBoolean("storage.mysql.enabled")) {
            this.database = new Database(this);
            database.initialize();
        } else {
            this.fileStorage = new FileStorage(this);
        }

        // Initialize session manager
        this.sessionManager = new SessionManager(this);

        // Initialize email sender if enabled
        if (getConfig().getBoolean("email.enabled")) {
            this.emailSender = new EmailSender(this);
        }

        // Initialize Bedrock support
        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            this.bedrockAuthHandler = new BedrockAuthHandler(this);
            getServer().getPluginManager().registerEvents(bedrockAuthHandler, this);
            getLogger().info("Bedrock support enabled (Floodgate detected)");
        }

        // Register events and commands
        registerEvents();
        registerCommands();

        // Start cleanup tasks
        startCleanupTasks();

        // Setup API
        CustomAuthAPI.setPlugin(this);

        getLogger().info("CustomAuth has been enabled!");
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerProtectionHandler(this), this);
    }

    private void registerCommands() {
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("logout").setExecutor(new LogoutCommand(this));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this));
        getCommand("authadmin").setExecutor(new AdminCommand(this));
    }

    private boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void scheduleTask(Runnable task, long delay, long period) {
        if (isFolia) {
            getServer().getGlobalRegionScheduler().runAtFixedRate(this, 
                scheduledTask -> task.run(), delay, period);
        } else {
            getServer().getScheduler().runTaskTimerAsynchronously(this, task, delay, period);
        }
    }

    private void startCleanupTasks() {
        // Session cleanup
        scheduleTask(() -> {
            sessionManager.cleanupSessions();
            playerCache.cleanUp();
        }, 20L * 60 * 30, 20L * 60 * 30); // Every 30 minutes
    }

    @Override
    public void onDisable() {
        // Shutdown async executor
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
        }

        // Save all data
        if (fileStorage != null) {
            fileStorage.saveAll();
        }
        if (database != null) {
            database.close();
        }

        getLogger().info("CustomAuth has been disabled!");
    }

    // Getters
    public DebugLogger getDebugLogger() { return debugLogger; }
    public boolean isFolia() { return isFolia; }
    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public Database getDatabase() { return database; }
    public FileStorage getFileStorage() { return fileStorage; }
    public SessionManager getSessionManager() { return sessionManager; }
    public BedrockAuthHandler getBedrockAuthHandler() { return bedrockAuthHandler; }
    public EmailSender getEmailSender() { return emailSender; }
    public SecurityUtils getSecurityUtils() { return securityUtils; }
    public Cache<String, PlayerData> getPlayerCache() { return playerCache; }
    public ExecutorService getAsyncExecutor() { return asyncExecutor; }

    // Helper methods
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor);
    }

    public void cachePlayerData(String username, PlayerData data) {
        playerCache.put(username.toLowerCase(), data);
    }

    public PlayerData getCachedPlayerData(String username) {
        return playerCache.getIfPresent(username.toLowerCase());
    }
}
