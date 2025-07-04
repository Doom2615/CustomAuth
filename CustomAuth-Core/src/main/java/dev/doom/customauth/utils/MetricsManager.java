// dev/doom/customauth/utils/MetricsManager.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsManager {
    private final CustomAuth plugin;
    private final Metrics metrics;
    private final AtomicInteger registrationsToday;
    private final AtomicInteger loginsToday;
    private final AtomicInteger bedrockPlayersToday;

    public MetricsManager(CustomAuth plugin, int pluginId) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, pluginId);
        this.registrationsToday = new AtomicInteger(0);
        this.loginsToday = new AtomicInteger(0);
        this.bedrockPlayersToday = new AtomicInteger(0);

        setupCharts();
        startDailyReset();
    }

    private void setupCharts() {
        // Storage type
        metrics.addCustomChart(new SimplePie("storage_type", 
            () -> plugin.getDatabase() != null ? "MySQL" : "File"));

        // Authentication method
        metrics.addCustomChart(new SimplePie("auth_method", () -> {
            if (plugin.getBedrockAuthHandler() != null && 
                plugin.getBedrockAuthHandler().getActiveBedrockPlayers() > 0) {
                return "Mixed";
            }
            return "Java";
        }));

        // Daily stats
        metrics.addCustomChart(new SingleLineChart("registrations", 
            registrationsToday::get));
        metrics.addCustomChart(new SingleLineChart("logins", 
            loginsToday::get));
        metrics.addCustomChart(new SingleLineChart("bedrock_players", 
            bedrockPlayersToday::get));
    }

    private void startDailyReset() {
        plugin.scheduleTask(() -> {
            registrationsToday.set(0);
            loginsToday.set(0);
            bedrockPlayersToday.set(0);
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
}
