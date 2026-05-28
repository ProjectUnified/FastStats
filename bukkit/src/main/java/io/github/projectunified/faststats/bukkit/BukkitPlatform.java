package io.github.projectunified.faststats.bukkit;

import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.DefaultConfig;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Bukkit implementation of {@link Platform}.
 */
public class BukkitPlatform implements Platform {
    private static final MethodHandle GET_PLUGINS_FOLDER;
    private static final MethodHandle SPIGOT;
    private static final MethodHandle GET_MINECRAFT_VERSION;
    private static final MethodHandle GET_SERVER_CONFIG;
    private static final MethodHandle GET_PLUGIN_META;
    private static final MethodHandle GET_PAPER_CONFIG;
    private static final MethodHandle GET_SPIGOT_CONFIG;
    private static final MethodHandle GET_CONFIGURATION_SECTION;
    private static final MethodHandle GET_BOOLEAN;

    static {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle getPluginsFolder = null;
        MethodHandle spigot = null;
        MethodHandle getMinecraftVersion = null;
        MethodHandle getServerConfig = null;
        MethodHandle getPluginMeta = null;
        MethodHandle getPaperConfig = null;
        MethodHandle getSpigotConfig = null;
        MethodHandle getConfigurationSection = null;
        MethodHandle getBoolean = null;

        try {
            getPluginsFolder = lookup.unreflect(Server.class.getMethod("getPluginsFolder"));
        } catch (Throwable ignored) {
        }

        try {
            spigot = lookup.unreflect(Server.class.getMethod("spigot"));
        } catch (Throwable ignored) {
        }

        try {
            getMinecraftVersion = lookup.unreflect(Server.class.getMethod("getMinecraftVersion"));
        } catch (Throwable ignored) {
        }

        try {
            getServerConfig = lookup.unreflect(Server.class.getMethod("getServerConfig"));
        } catch (Throwable ignored) {
        }

        try {
            getPluginMeta = lookup.unreflect(Plugin.class.getMethod("getPluginMeta"));
        } catch (Throwable ignored) {
        }

        try {
            Class<?> configSectionClass = Class.forName("org.bukkit.configuration.ConfigurationSection");
            getConfigurationSection = lookup.unreflect(configSectionClass.getMethod("getConfigurationSection", String.class));
            getBoolean = lookup.unreflect(configSectionClass.getMethod("getBoolean", String.class));
        } catch (Throwable ignored) {
        }

        try {
            Class<?> spigotClass = Class.forName("org.bukkit.Server$Spigot");
            getPaperConfig = lookup.unreflect(spigotClass.getMethod("getPaperConfig"));
            getSpigotConfig = lookup.unreflect(spigotClass.getMethod("getSpigotConfig"));
        } catch (Throwable ignored) {
        }

        GET_PLUGINS_FOLDER = getPluginsFolder;
        SPIGOT = spigot;
        GET_MINECRAFT_VERSION = getMinecraftVersion;
        GET_SERVER_CONFIG = getServerConfig;
        GET_PLUGIN_META = getPluginMeta;
        GET_PAPER_CONFIG = getPaperConfig;
        GET_SPIGOT_CONFIG = getSpigotConfig;
        GET_CONFIGURATION_SECTION = getConfigurationSection;
        GET_BOOLEAN = getBoolean;
    }

    private final Plugin plugin;
    private final Config config;
    private final List<Metric<?>> defaultMetrics;

    /**
     * Constructs a new {@link BukkitPlatform} for the given plugin.
     *
     * @param plugin the plugin instance
     */
    public BukkitPlatform(Plugin plugin) {
        this.plugin = plugin;

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

        // Minecraft Version
        defaultMetrics.add(Metric.string("minecraft_version", () -> {
            try {
                if (GET_MINECRAFT_VERSION != null) {
                    return (String) GET_MINECRAFT_VERSION.invoke(server);
                }
            } catch (Throwable ignored) {
            }

            try {
                return server.getBukkitVersion().split("-", 2)[0];
            } catch (Exception ex) {
                return server.getVersion().split("\\(MC: |\\)", 3)[1];
            }
        }));

        // Online Mode
        defaultMetrics.add(Metric.bool("online_mode", () -> {
            try {
                if (GET_SERVER_CONFIG != null) {
                    Object serverConfig = GET_SERVER_CONFIG.invoke(server);
                    MethodHandle isProxyOnlineMode = MethodHandles.publicLookup()
                            .unreflect(serverConfig.getClass().getMethod("isProxyOnlineMode"));
                    return (Boolean) isProxyOnlineMode.invoke(serverConfig);
                }
            } catch (Throwable ignored) {
            }

            try {
                return isProxyOnlineMode();
            } catch (Exception ex) {
                return server.getOnlineMode();
            }
        }));

        // Player Count
        defaultMetrics.add(Metric.number("player_count", () -> {
            try {
                return server.getOnlinePlayers().size();
            } catch (Throwable t) {
                return 0;
            }
        }));

        // Plugin Version
        defaultMetrics.add(Metric.string("plugin_version", () -> {
            try {
                if (GET_PLUGIN_META != null) {
                    Object pluginMeta = GET_PLUGIN_META.invoke(plugin);
                    MethodHandle getVersion = MethodHandles.publicLookup()
                            .unreflect(pluginMeta.getClass().getMethod("getVersion"));
                    return (String) getVersion.invoke(pluginMeta);
                }
            } catch (Throwable ignored) {
            }

            return plugin.getDescription().getVersion();
        }));

        // Server Type
        defaultMetrics.add(Metric.string("server_type", server::getName));
    }

    private boolean isProxyOnlineMode() {
        if (SPIGOT == null) {
            return false;
        }

        Server server = plugin.getServer();
        Object spigot;
        try {
            spigot = SPIGOT.invoke(server);
        } catch (Throwable e) {
            return false;
        }

        Object proxies = null;
        try {
            if (GET_PAPER_CONFIG != null && GET_CONFIGURATION_SECTION != null && GET_BOOLEAN != null) {
                Object paperConfig = GET_PAPER_CONFIG.invoke(spigot);
                proxies = GET_CONFIGURATION_SECTION.invoke(paperConfig, "proxies");
                if (proxies != null) {
                    boolean velocityEnabled = (boolean) GET_BOOLEAN.invoke(proxies, "velocity.enabled");
                    boolean velocityOnline = (boolean) GET_BOOLEAN.invoke(proxies, "velocity.online-mode");
                    if (velocityEnabled && velocityOnline) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (GET_SPIGOT_CONFIG != null && GET_CONFIGURATION_SECTION != null && GET_BOOLEAN != null) {
                Object spigotConfig = GET_SPIGOT_CONFIG.invoke(spigot);
                Object settings = GET_CONFIGURATION_SECTION.invoke(spigotConfig, "settings");
                if (settings != null) {
                    boolean bungee = (boolean) GET_BOOLEAN.invoke(settings, "bungeecord");
                    if (bungee && proxies != null) {
                        return (boolean) GET_BOOLEAN.invoke(proxies, "bungee-cord.online-mode");
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
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
