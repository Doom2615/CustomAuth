// CustomAuthMetrics.java
package dev.doom.customauth;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomAuthMetrics {
    private final CustomAuth plugin;
    private final Metrics metrics;
    private final AtomicInteger registrationsToday = new AtomicInteger(0);
    private final AtomicInteger loginsToday = new AtomicInteger(0);

    public CustomAuthMetrics(CustomAuth plugin) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, 12345); // Replace with actual plugin ID
        setupCharts();
        startDailyReset();
    }

    private void setupCharts() {
        metrics.addCustomChart(new SimplePie("database_type", 
            () -> plugin.getConfig().getBoolean("mysql.enabled") ? "MySQL" : "SQLite"));
            
        metrics.addCustomChart(new SimplePie("email_verification", 
            () -> plugin.getConfig().getBoolean("email.enabled") ? "Enabled" : "Disabled"));
            
        metrics.addCustomChart(new SingleLineChart("registrations", 
            registrationsToday::get));
            
        metrics.addCustomChart(new SingleLineChart("logins", 
            loginsToday::get));
    }

    private void startDailyReset() {
        // Schedule daily reset using Folia's scheduler
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, 
            task -> {
                registrationsToday.set(0);
                loginsToday.set(0);
            }, 
            1200L, // 1 minute delay
            24000L // Run every 24 hours
        );
    }

    public void incrementRegistrations() {
        registrationsToday.incrementAndGet();
    }

    public void incrementLogins() {
        loginsToday.incrementAndGet();
    }
}
