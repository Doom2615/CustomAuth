// Main class (dev/doom/customauth/CustomAuth.java)
package dev.doom.customauth;

import dev.doom.customauth.bedrock.BedrockAuthHandler;
import dev.doom.customauth.handlers.*;
import dev.doom.customauth.storage.*;
import dev.doom.customauth.metrics.CustomAuthMetrics;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.java.JavaPlugin;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.*;
import java.time.Duration;

public class CustomAuth extends JavaPlugin {
    private boolean isFolia;
    private final Cache<String, PlayerData> playerCache;
    private final RateLimiter loginRateLimiter;
    private final ExecutorService asyncExecutor;
    private Database database;
    private FileStorage fileStorage;
    private ConfigManager configManager;
    private SecurityManager securityManager;
    private SessionManager sessionManager;
    private EmailVerification emailVerification;
    private CustomAuthMetrics metrics;
    private BedrockAuthHandler bedrockAuthHandler;

    public CustomAuth() {
        this.playerCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
        this.loginRateLimiter = RateLimiter.create(10.0);
        this.asyncExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
    }

    @Override
    public void onEnable() {
        // Check for Folia
        this.isFolia = checkFolia();

        // Initialize configurations
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.securityManager = new SecurityManager(this);
        this.sessionManager = new SessionManager(this);

        // Initialize storage
        if (getConfig().getBoolean("mysql.enabled")) {
            this.database = new Database(this);
            database.initialize();
        } else {
            this.fileStorage = new FileStorage(this);
        }

        // Initialize email verification if enabled
        if (getConfig().getBoolean("email.enabled")) {
            this.emailVerification = new EmailVerification(this);
        }

        // Initialize metrics
        this.metrics = new CustomAuthMetrics(this);

        // Initialize Bedrock support
        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            this.bedrockAuthHandler = new BedrockAuthHandler(this);
            getServer().getPluginManager().registerEvents(bedrockAuthHandler, this);
        }

        // Register events and commands
        registerHandlers();
        registerCommands();

        // Start cleanup task
        startCleanupTask();

        getLogger().info("CustomAuth has been enabled!");
    }

    private void registerHandlers() {
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerProtectionHandler(this), this);
    }

    private void registerCommands() {
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this));
        getCommand("logout").setExecutor(new LogoutCommand(this));
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

    private void startCleanupTask() {
        scheduleTask(() -> {
            sessionManager.cleanupSessions();
            securityManager.cleanupIpBans();
            playerCache.cleanUp();
        }, 20L * 60 * 30, 20L * 60 * 30); // Run every 30 minutes
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

        // Save all cached data
        if (fileStorage != null) {
            fileStorage.saveAll();
        }
        if (database != null) {
            database.close();
        }

        getLogger().info("CustomAuth has been disabled!");
    }

    // Getters
    public boolean isFolia() { return isFolia; }
    public Cache<String, PlayerData> getPlayerCache() { return playerCache; }
    public RateLimiter getLoginRateLimiter() { return loginRateLimiter; }
    public ExecutorService getAsyncExecutor() { return asyncExecutor; }
    public Database getDatabase() { return database; }
    public FileStorage getFileStorage() { return fileStorage; }
    public ConfigManager getConfigManager() { return configManager; }
    public SecurityManager getSecurityManager() { return securityManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public EmailVerification getEmailVerification() { return emailVerification; }
    public CustomAuthMetrics getMetrics() { return metrics; }
    public BedrockAuthHandler getBedrockAuthHandler() { return bedrockAuthHandler; }

    // Helper methods
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor);
    }

    public boolean canProcessLogin() {
        return loginRateLimiter.tryAcquire();
    }

    public void cachePlayerData(String username, PlayerData data) {
        playerCache.put(username.toLowerCase(), data);
    }

    public PlayerData getCachedPlayerData(String username) {
        return playerCache.getIfPresent(username.toLowerCase());
    }
      }
