package io.github.projectunified.faststats.minestom;

import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Minestom implementation of {@link Platform}.
 */
public class MinestomPlatform implements Platform {
    private final Logger logger = LoggerFactory.getLogger(MinestomPlatform.class);
    private final Config config;
    private final List<Metric<?>> defaultMetrics;

    /**
     * Constructs a new {@link MinestomPlatform}.
     */
    public MinestomPlatform() {
        Path configPath = Path.of("faststats", "config.properties");
        this.config = DefaultConfig.read(configPath);
        this.defaultMetrics = new ArrayList<>();
        setupDefaultMetrics();
    }

    private void setupDefaultMetrics() {
        defaultMetrics.add(Metric.string("minecraft_version", () -> MinecraftServer.VERSION_NAME));
        defaultMetrics.add(Metric.bool("online_mode", () -> !(MinecraftServer.process().auth() instanceof Auth.Offline)));
        defaultMetrics.add(Metric.number("player_count", () -> MinecraftServer.getConnectionManager().getOnlinePlayerCount()));
        defaultMetrics.add(Metric.string("server_type", () -> "Minestom"));
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
