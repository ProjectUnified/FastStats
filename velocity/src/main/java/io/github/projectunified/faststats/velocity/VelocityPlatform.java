package io.github.projectunified.faststats.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Velocity implementation of {@link Platform}.
 */
public class VelocityPlatform implements Platform {
    private final ProxyServer server;
    private final PluginContainer plugin;
    private final Logger logger;
    private final Config config;
    private final List<Metric<?>> defaultMetrics;

    /**
     * Constructs a new {@link VelocityPlatform} for the given plugin.
     *
     * @param server        the velocity proxy server
     * @param plugin        the plugin container
     * @param logger        the logger
     * @param dataDirectory the data directory
     */
    public VelocityPlatform(ProxyServer server, PluginContainer plugin, Logger logger, Path dataDirectory) {
        this.server = server;
        this.plugin = plugin;
        this.logger = logger;
        Path configPath = dataDirectory.resolveSibling("faststats").resolve("config.properties");
        this.config = DefaultConfig.read(configPath);
        this.defaultMetrics = new ArrayList<>();
        setupDefaultMetrics();
    }

    private void setupDefaultMetrics() {
        defaultMetrics.add(Metric.bool("online_mode", () -> server.getConfiguration().isOnlineMode()));
        defaultMetrics.add(Metric.number("player_count", server::getPlayerCount));
        defaultMetrics.add(Metric.string("plugin_version", () -> plugin.getDescription().getVersion().orElse("unknown")));
        defaultMetrics.add(Metric.string("proxy_version", () -> server.getVersion().getVersion()));
        defaultMetrics.add(Metric.string("server_type", () -> server.getVersion().getName()));
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
