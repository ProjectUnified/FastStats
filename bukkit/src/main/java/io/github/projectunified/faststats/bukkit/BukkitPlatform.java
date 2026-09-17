package io.github.projectunified.faststats.bukkit;

import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Bukkit implementation of {@link Platform}.
 */
public class BukkitPlatform implements Platform {
    private static final Method GET_PLUGINS_FOLDER;
    private static final Method SPIGOT;
    private static final Method GET_MINECRAFT_VERSION;
    private static final Method GET_SERVER_CONFIG;
    private static final Method GET_PAPER_CONFIG;
    private static final Method GET_SPIGOT_CONFIG;
    private static final Method GET_ONLINE_PLAYERS;
    private static final boolean GET_ONLINE_PLAYERS_IS_COLLECTION;

    static {
        Method getPluginsFolder = null;
        Method spigot = null;
        Method getMinecraftVersion = null;
        Method getServerConfig = null;
        Method getPaperConfig = null;
        Method getSpigotConfig = null;
        Method getOnlinePlayers = null;
        boolean getOnlinePlayersIsCollection = false;

        try {
            getPluginsFolder = Server.class.getMethod("getPluginsFolder");
        } catch (Throwable ignored) {
        }

        try {
            spigot = Server.class.getMethod("spigot");
        } catch (Throwable ignored) {
        }

        try {
            getMinecraftVersion = Server.class.getMethod("getMinecraftVersion");
        } catch (Throwable ignored) {
        }

        try {
            getServerConfig = Server.class.getMethod("getServerConfig");
        } catch (Throwable ignored) {
        }

        try {
            Class<?> spigotClass = Class.forName("org.bukkit.Server$Spigot");
            getPaperConfig = spigotClass.getMethod("getPaperConfig");
            getSpigotConfig = spigotClass.getMethod("getSpigotConfig");
        } catch (Throwable ignored) {
        }

        try {
            getOnlinePlayers = Server.class.getMethod("getOnlinePlayers");
            getOnlinePlayersIsCollection = Collection.class.isAssignableFrom(getOnlinePlayers.getReturnType());
        } catch (Throwable ignored) {
        }

        GET_PLUGINS_FOLDER = getPluginsFolder;
        SPIGOT = spigot;
        GET_MINECRAFT_VERSION = getMinecraftVersion;
        GET_SERVER_CONFIG = getServerConfig;
        GET_PAPER_CONFIG = getPaperConfig;
        GET_SPIGOT_CONFIG = getSpigotConfig;
        GET_ONLINE_PLAYERS = getOnlinePlayers;
        GET_ONLINE_PLAYERS_IS_COLLECTION = getOnlinePlayersIsCollection;
    }

    private final Plugin plugin;
    private final Config config;
    private final List<Metric<?>> defaultMetrics;
    private final String serverVersion;

    /**
     * Constructs a new {@link BukkitPlatform} for the given plugin.
     *
     * @param plugin the plugin instance
     */
    public BukkitPlatform(Plugin plugin) {
        this.plugin = plugin;
        this.serverVersion = plugin.getServer().getVersion().split("\\(MC: |\\)", 2)[0].trim();

        File pluginsFolder = null;
        try {
            if (GET_PLUGINS_FOLDER != null) {
                pluginsFolder = (File) GET_PLUGINS_FOLDER.invoke(plugin.getServer());
            }
        } catch (Throwable ignored) {
        }

        if (pluginsFolder == null) {
            pluginsFolder = plugin.getDataFolder().getParentFile();
        }

        Path configPath = pluginsFolder.toPath().resolve("faststats").resolve("config.properties");
        this.config = DefaultConfig.read(configPath);

        this.defaultMetrics = new ArrayList<>();
        setupDefaultMetrics();
    }

    private void setupDefaultMetrics() {
        final Server server = plugin.getServer();

        // Game Version
        defaultMetrics.add(Metric.string("game_version", () -> {
            try {
                if (GET_MINECRAFT_VERSION != null) {
                    return (String) GET_MINECRAFT_VERSION.invoke(server);
                }
            } catch (Throwable ignored) {
            }

            try {
                return server.getBukkitVersion().split("-", 2)[0];
            } catch (Throwable ignored) {
            }

            try {
                return server.getVersion().split("\\(MC: |\\)", 3)[1];
            } catch (Throwable ignored) {
                return serverVersion;
            }
        }));

        // Online Mode
        defaultMetrics.add(Metric.bool("online_mode", () -> {
            if (GET_SERVER_CONFIG != null) {
                try {
                    Object serverConfig = GET_SERVER_CONFIG.invoke(server);
                    Method isProxyOnlineMode = serverConfig.getClass().getMethod("isProxyOnlineMode");
                    return (Boolean) isProxyOnlineMode.invoke(serverConfig);
                } catch (Throwable ignored) {
                }
            }

            if (SPIGOT != null && (GET_PAPER_CONFIG != null || GET_SPIGOT_CONFIG != null)) {
                try {
                    return isProxyOnlineMode();
                } catch (Throwable ignored) {
                }
            }

            return server.getOnlineMode();
        }));

        // Platform Version
        defaultMetrics.add(Metric.string("platform_version", () -> serverVersion));

        // Player Count
        defaultMetrics.add(Metric.number("player_count", () -> {
            try {
                if (GET_ONLINE_PLAYERS != null) {
                    Object onlinePlayers = GET_ONLINE_PLAYERS.invoke(server);
                    if (GET_ONLINE_PLAYERS_IS_COLLECTION) {
                        return ((Collection<?>) onlinePlayers).size();
                    } else {
                        return ((Object[]) onlinePlayers).length;
                    }
                }
            } catch (Throwable ignored) {
            }
            return 0;
        }));

        // Plugin Version
        defaultMetrics.add(Metric.string("plugin_version", () -> plugin.getDescription().getVersion()));

        // Server Type
        defaultMetrics.add(Metric.string("server_type", server::getName));
    }

    /**
     * Checks the proxy online mode configured in the Paper/Spigot configuration.
     * <p>
     * Fails with a reflective exception if the required server API is unavailable,
     * so callers can fall back to {@link Server#getOnlineMode()}.
     *
     * @return true if the proxy is configured to run in online mode
     * @throws ReflectiveOperationException if the server API is unavailable
     */
    private boolean isProxyOnlineMode() throws ReflectiveOperationException {
        Server server = plugin.getServer();
        Object spigot = SPIGOT.invoke(server);

        ConfigurationSection proxies = null;
        if (GET_PAPER_CONFIG != null) {
            Object paperConfig = GET_PAPER_CONFIG.invoke(spigot);
            if (paperConfig instanceof ConfigurationSection) {
                ConfigurationSection section = (ConfigurationSection) paperConfig;
                proxies = section.getConfigurationSection("proxies");
                if (proxies != null
                        && proxies.getBoolean("velocity.enabled")
                        && proxies.getBoolean("velocity.online-mode")) {
                    return true;
                }
            }
        }

        if (GET_SPIGOT_CONFIG != null) {
            Object spigotConfig = GET_SPIGOT_CONFIG.invoke(spigot);
            if (spigotConfig instanceof ConfigurationSection) {
                ConfigurationSection settings = ((ConfigurationSection) spigotConfig).getConfigurationSection("settings");
                if (settings != null && settings.getBoolean("bungeecord") && proxies != null) {
                    return proxies.getBoolean("bungee-cord.online-mode");
                }
            }
        }

        return false;
    }

    @Override
    public String getProjectName() {
        return plugin.getName();
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
