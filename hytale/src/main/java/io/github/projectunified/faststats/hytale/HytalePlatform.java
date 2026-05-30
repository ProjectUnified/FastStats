package io.github.projectunified.faststats.hytale;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Hytale implementation of {@link Platform}.
 */
public class HytalePlatform implements Platform {
    private final JavaPlugin plugin;
    private final Config config;
    private final List<Metric<?>> defaultMetrics;

    /**
     * Constructs a new {@link HytalePlatform} for the given plugin.
     *
     * @param plugin the plugin instance
     */
    public HytalePlatform(JavaPlugin plugin) {
        this.plugin = plugin;
        Path configPath = plugin.getDataDirectory().toAbsolutePath().getParent().resolve("faststats").resolve("config.properties");
        this.config = DefaultConfig.read(configPath);
        this.defaultMetrics = new ArrayList<>();
        setupDefaultMetrics();
    }

    private void setupDefaultMetrics() {
        defaultMetrics.add(Metric.string("server_version", () -> HytaleServer.get().getServerName()));
        defaultMetrics.add(Metric.number("player_count", () -> Universe.get().getPlayerCount()));
        defaultMetrics.add(Metric.string("server_type", () -> "Hytale"));
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public Collection<Metric<?>> getMetrics() {
        return defaultMetrics;
    }

    @Override
    public void logInfo(String message) {
        plugin.getLogger().atInfo().log(message);
    }

    @Override
    public void logWarning(String message) {
        plugin.getLogger().atWarning().log(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        plugin.getLogger().atSevere().log(message, throwable);
    }
}
