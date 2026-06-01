package io.github.projectunified.faststats.bukkit.testplugin;

import io.github.projectunified.faststats.bukkit.BukkitPlatform;
import io.github.projectunified.faststats.core.Metrics;
import io.github.projectunified.faststats.errortracker.ErrorTracker;
import io.github.projectunified.faststats.gson.GsonSerializer;
import io.github.projectunified.faststats.net.NetSubmitter;
import org.bukkit.plugin.java.JavaPlugin;

public class FastStatsTestPlugin extends JavaPlugin {
    private Metrics metrics;

    @Override
    public void onEnable() {
        BukkitPlatform platform = new BukkitPlatform(this);
        metrics = Metrics.builder()
                .platform(platform)
                .serializer(new GsonSerializer())
                .submitter(new NetSubmitter("2910630464716344afc261e21f85c6a6"))
                .addFeature(ErrorTracker.contextAware())
                .build();
        metrics.start();
        getLogger().info("FastStats Bukkit Test Plugin started.");

        try {
            throw new RuntimeException("Test manual exception");
        } catch (Exception e) {
            metrics.getFeature(ErrorTracker.class).ifPresent(tracker -> {
                tracker.trackError(e);
                getLogger().info("Tracked test manual exception.");
            });
        }
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        getLogger().info("FastStats Bukkit Test Plugin stopped.");
    }
}
