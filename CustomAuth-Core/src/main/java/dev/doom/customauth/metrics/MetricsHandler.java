// dev/doom/customauth/metrics/MetricsHandler.java
package dev.doom.customauth.metrics;

import dev.doom.customauth.CustomAuth;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.charts.DrilldownPie;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsHandler {
    private final CustomAuth plugin;
    private final Metrics metrics;
    private final AtomicInteger registrationsToday;
    private final AtomicInteger loginsToday;
    private final AtomicInteger bedrockPlayersToday;
    private final AtomicInteger failedAttemptsToday;

    public MetricsHandler(CustomAuth plugin, int pluginId) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, pluginId);
        this.registrationsToday = new AtomicInteger(0);
        this.loginsToday = new AtomicInteger(0);
        this.bedrockPlayersToday = new AtomicInteger(0);
        this.failedAttemptsToday = new AtomicInteger(0);

        setupCharts();
        startDailyReset();
    }

    private void setupCharts() {
        // Storage type chart
        metrics.addCustomChart(new SimplePie("storage_type", () -> 
            plugin.getConfig().getBoolean("mysql.enabled") ? "MySQL" : "File"));

        // Authentication method chart
        metrics.addCustomChart(new DrilldownPie("auth_methods", () -> {
            Map<String, Map<String, Integer>> map = new HashMap<>();
            Map<String, Integer> entry = new HashMap<>();
            
            int javaPlayers = plugin.getPlayerCache().asMap().size() - 
                             plugin.getBedrockAuthHandler().getActiveBedrockPlayers();
            int bedrockPlayers = plugin.getBedrockAuthHandler().getActiveBedrockPlayers();
            
            entry.put("Java", javaPlayers);
            entry.put("Bedrock", bedrockPlayers);
            
            map.put("Players", entry);
            return map;
        }));

        // Email verification chart
        metrics.addCustomChart(new SimplePie("email_verification", () ->
            plugin.getConfig().getBoolean("email.enabled") ? "Enabled" : "Disabled"));

        // Session management chart
        metrics.addCustomChart(new SimplePie("session_management", () ->
            plugin.getConfig().getBoolean("session.enabled") ? "Enabled" : "Disabled"));

        // Daily statistics charts
        metrics.addCustomChart(new SingleLineChart("registrations", 
            registrationsToday::get));
        
        metrics.addCustomChart(new SingleLineChart("logins", 
            loginsToday::get));
        
        metrics.addCustomChart(new SingleLineChart("bedrock_players", 
            bedrockPlayersToday::get));
        
        metrics.addCustomChart(new SingleLineChart("failed_attempts", 
            failedAttemptsToday::get));

        // Server type chart
        metrics.addCustomChart(new SimplePie("server_type", () ->
            plugin.isFolia() ? "Folia" : "Paper"));
    }

    private void startDailyReset() {
        plugin.scheduleTask(() -> {
            registrationsToday.set(0);
            loginsToday.set(0);
            bedrockPlayersToday.set(0);
            failedAttemptsToday.set(0);
        }, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24); // Reset every 24 hours
    }

    public void incrementRegistrations() {
        registrationsToday.incrementAndGet();
    }

    public void incrementLogins() {
        loginsToday.incrementAndGet();
    }

    public void incrementBedrockPlayers() {
        bedrockPlayersToday.incrementAndGet();
    }

    public void incrementFailedAttempts() {
        failedAttemptsToday.incrementAndGet();
    }

    public int getRegistrationsToday() {
        return registrationsToday.get();
    }

    public int getLoginsToday() {
        return loginsToday.get();
    }

    public int getBedrockPlayersToday() {
        return bedrockPlayersToday.get();
    }

    public int getFailedAttemptsToday() {
        return failedAttemptsToday.get();
    }
                                             }
