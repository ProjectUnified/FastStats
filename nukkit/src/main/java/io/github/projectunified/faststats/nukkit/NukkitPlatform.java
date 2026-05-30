package io.github.projectunified.faststats.nukkit;

import cn.nukkit.Server;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Logger;
import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Nukkit implementation of {@link Platform}.
 */
public class NukkitPlatform implements Platform {
    private final PluginBase plugin;
    private final Logger logger;
    private final Config config;
    private final List<Metric<?>> defaultMetrics;

    /**
     * Constructs a new {@link NukkitPlatform} for the given plugin.
     *
     * @param plugin the plugin instance
     */
    public NukkitPlatform(PluginBase plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        Path configPath = new File(plugin.getServer().getPluginPath(), "faststats").toPath().resolve("config.properties");
        this.config = DefaultConfig.read(configPath);
        this.defaultMetrics = new ArrayList<>();
        setupDefaultMetrics();
    }

    private void setupDefaultMetrics() {
        Server server = plugin.getServer();
        defaultMetrics.add(Metric.string("minecraft_version", server::getVersion));
        defaultMetrics.add(Metric.bool("online_mode", () -> server.xboxAuth));
        defaultMetrics.add(Metric.number("player_count", server::getOnlinePlayersCount));
        defaultMetrics.add(Metric.string("plugin_version", () -> plugin.getDescription().getVersion()));
        defaultMetrics.add(Metric.string("server_type", server::getName));
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
        logger.warning(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
