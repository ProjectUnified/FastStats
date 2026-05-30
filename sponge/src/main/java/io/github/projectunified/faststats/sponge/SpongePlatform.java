package io.github.projectunified.faststats.sponge;

import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Platform.Component;
import org.spongepowered.api.Sponge;
import org.spongepowered.plugin.PluginContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sponge implementation of {@link Platform}.
 */
public class SpongePlatform implements Platform {
    private final PluginContainer plugin;
    private final Logger logger;
    private final Config config;
    private final List<Metric<?>> defaultMetrics;

    /**
     * Constructs a new {@link SpongePlatform} for the given plugin container.
     *
     * @param plugin        the plugin container
     * @param logger        the logger instance
     * @param dataDirectory the data directory
     */
    public SpongePlatform(PluginContainer plugin, Logger logger, Path dataDirectory) {
        this.plugin = plugin;
        this.logger = logger;
        Path configPath = dataDirectory.resolve("faststats").resolve("config.properties");
        this.config = DefaultConfig.read(configPath);
        this.defaultMetrics = new ArrayList<>();
        setupDefaultMetrics();
    }

    private void setupDefaultMetrics() {
        defaultMetrics.add(Metric.bool("online_mode", () -> Sponge.server().isOnlineModeEnabled()));
        defaultMetrics.add(Metric.number("player_count", () -> Sponge.server().onlinePlayers().size()));
        defaultMetrics.add(Metric.string("plugin_version", () -> plugin.metadata().version().toString()));
        defaultMetrics.add(Metric.string("minecraft_version", () -> Sponge.platform().minecraftVersion().name()));
        defaultMetrics.add(Metric.string("server_type", () -> Sponge.platform().container(Component.IMPLEMENTATION).metadata().id()));
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
        logger.info(message);
    }

    @Override
    public void logWarning(String message) {
        logger.warn(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
