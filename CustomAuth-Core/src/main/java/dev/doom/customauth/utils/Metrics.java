// dev/doom/customauth/utils/Metrics.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.charts.DrilldownPie;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;

public class CustomAuthMetrics {
    private final CustomAuth plugin;
    private final Metrics metrics;
    private final AtomicInteger registrationsToday = new AtomicInteger(0);
    private final AtomicInteger loginsToday = new AtomicInteger(0);
    private final AtomicInteger bedrockPlayersToday = new AtomicInteger(0);

    public CustomAuthMetrics(CustomAuth plugin, int pluginId) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, pluginId);
        setupCharts();
    }

    private void setupCharts() {
        // Storage type chart
        metrics.addCustomChart(new SimplePie("storage_type", () -> 
            plugin.getConfig().getBoolean("mysql.enabled") ? "MySQL" : "SQLite"));

        // Authentication method chart
        metrics.addCustomChart(new DrilldownPie("auth_methods", () -> {
            HashMap<String, Map<String, Integer>> map = new HashMap<>();
            HashMap<String, Integer> entry = new HashMap<>();
            
            entry.put("Password", plugin.getPlayerCache().asMap().size());
            entry.put("Session", plugin.getSessionManager().getActiveSessions());
            entry.put("Bedrock", plugin.getBedrockAuthHandler().getActiveBedrockPlayers());
            
            map.put("Authentication", entry);
            return map;
        }));

        // Registration chart
        metrics.addCustomChart(new SingleLineChart("registrations", 
            registrationsToday::get));

        // Login chart
        metrics.addCustomChart(new SingleLineChart("logins", 
            loginsToday::get));

        // Bedrock players chart
        metrics.addCustomChart(new SingleLineChart("bedrock_players", 
            bedrockPlayersToday::get));
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

    public void resetDailyStats() {
        registrationsToday.set(0);
        loginsToday.set(0);
        bedrockPlayersToday.set(0);
    }
}
