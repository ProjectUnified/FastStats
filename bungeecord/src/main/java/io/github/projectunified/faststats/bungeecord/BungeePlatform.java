package io.github.projectunified.faststats.bungeecord;

import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * BungeeCord implementation of {@link Platform}.
 */
public class BungeePlatform implements Platform {
    private final Plugin plugin;
    private final Config config;
    private final List<Metric<?>> defaultMetrics;

    /**
     * Constructs a new {@link BungeePlatform} for the given plugin.
     *
     * @param plugin the plugin instance
     */
    public BungeePlatform(Plugin plugin) {
        this.plugin = plugin;
        Path configPath = plugin.getProxy().getPluginsFolder().toPath().resolve("faststats").resolve("config.properties");
        this.config = DefaultConfig.read(configPath);
        this.defaultMetrics = new ArrayList<>();
        setupDefaultMetrics();
    }

    private void setupDefaultMetrics() {
        ProxyServer server = plugin.getProxy();
        defaultMetrics.add(Metric.bool("online_mode", () -> server.getConfig().isOnlineMode()));
        defaultMetrics.add(Metric.number("player_count", server::getOnlineCount));
        defaultMetrics.add(Metric.string("plugin_version", () -> plugin.getDescription().getVersion()));
        defaultMetrics.add(Metric.string("proxy_version", server::getVersion));
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
        plugin.getLogger().info(message);
    }

    @Override
    public void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, message, throwable);
    }
}
