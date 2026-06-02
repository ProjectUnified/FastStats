package io.github.projectunified.faststats.bukkit.testplugin;

import io.github.projectunified.faststats.bukkit.BukkitPlatform;
import io.github.projectunified.faststats.core.Metrics;
import io.github.projectunified.faststats.errortracker.ErrorTracker;
import io.github.projectunified.faststats.featureflag.FeatureFlag;
import io.github.projectunified.faststats.featureflag.FeatureFlagManager;
import io.github.projectunified.faststats.gson.GsonSerializer;
import io.github.projectunified.faststats.net.NetSubmitter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class FastStatsTestPlugin extends JavaPlugin {
    private Metrics metrics;

    @Override
    public void onEnable() {
        BukkitPlatform platform = new BukkitPlatform(this);
        FeatureFlagManager flagManager = new FeatureFlagManager();
        FeatureFlag<Boolean> testFlag = flagManager.define("test-flag", false);
        FeatureFlag<String> testStringFlag = flagManager.define("test-string-flag", "test_string");
        FeatureFlag<Number> testNumberFlag = flagManager.define("test-number-flag", 10);

        metrics = Metrics.builder()
                .platform(platform)
                .serializer(new GsonSerializer())
                .submitter(new NetSubmitter("2910630464716344afc261e21f85c6a6"))
                .addFeature(ErrorTracker.contextAware())
                .addFeature(flagManager)
                .build();
        metrics.start();
        getLogger().info("FastStats Bukkit Test Plugin started.");

        testFlag.whenReady().thenAccept(value -> {
            getLogger().info("Feature flag 'test_flag' value: " + value);
        }).exceptionally(ex -> {
            getLogger().log(Level.WARNING, "Failed to fetch feature flag 'test_flag': ", ex);
            return null;
        });
        testStringFlag.whenReady().thenAccept(value -> {
            getLogger().info("Feature flag 'test_string_flag' value: " + value);
        }).exceptionally(ex -> {
            getLogger().log(Level.WARNING, "Failed to fetch feature flag 'test_string_flag': ", ex);
            return null;
        });
        testNumberFlag.whenReady().thenAccept(value -> {
            getLogger().info("Feature flag 'test_number_flag' value: " + value);
        }).exceptionally(ex -> {
            getLogger().log(Level.WARNING, "Failed to fetch feature flag 'test_number_flag': ", ex);
            return null;
        });

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
