package io.github.projectunified.faststats.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link Config} that reads and writes configurations
 * from a properties file.
 */
public class DefaultConfig implements Config {
    public static final String[] DEFAULT_COMMENT = {
            " FastStats (https://faststats.dev) collects anonymous usage statistics for plugin developers.",
            "# This helps developers understand how their projects are used in the real world.",
            "#",
            "# No IP addresses, player data, or personal information is collected.",
            "# The server ID below is randomly generated and can be regenerated at any time.",
            "#",
            "# Enabling metrics has no noticeable performance impact.",
            "# Keeping metrics enabled is recommended, but you can opt out by setting",
            "# 'enabled=false' in plugins/faststats/config.properties.",
            "#",
            "# If you suspect a plugin is collecting personal data or bypassing the \"enabled\" option,",
            "# please report it at: https://faststats.dev/abuse",
            "#",
            "# For more information, visit: https://faststats.dev/info"
    };

    private final Path file;
    private final String[] comment;
    private final boolean externallyManaged;
    private final Properties properties;
    private final boolean firstRun;

    private final UUID serverId;
    private final boolean additionalMetrics;
    private final boolean submitMetrics;
    private final boolean debug;
    private final boolean enabled;
    private final int oldConfigVersion;

    /**
     * Constructs a new {@link DefaultConfig} instance.
     *
     * @param file              the config file path
     * @param comment           the comment header
     * @param externallyManaged true if configuration is externally managed
     * @param properties        the raw properties
     * @param firstRun          whether it is the first time running stats
     * @param serverId          the server ID
     * @param additionalMetrics whether to submit additional metrics
     * @param submitMetrics     whether to submit metrics
     * @param debug             whether debug logging is enabled
     * @param enabled           whether metrics collection is enabled
     * @param oldConfigVersion  the configuration version before upgrade
     */
    public DefaultConfig(Path file, String[] comment, boolean externallyManaged, Properties properties, boolean firstRun,
                         UUID serverId, boolean additionalMetrics, boolean submitMetrics, boolean debug, boolean enabled, int oldConfigVersion) {
        this.file = file;
        this.comment = comment;
        this.externallyManaged = externallyManaged;
        this.properties = properties;
        this.firstRun = firstRun;
        this.serverId = serverId;
        this.additionalMetrics = additionalMetrics;
        this.submitMetrics = submitMetrics;
        this.debug = debug;
        this.enabled = enabled;
        this.oldConfigVersion = oldConfigVersion;
    }

    /**
     * Reads a config from the specified path with default settings.
     *
     * @param file the path to the config file
     * @return the loaded DefaultConfig
     * @throws RuntimeException if loading or saving fails
     */
    public static DefaultConfig read(Path file) throws RuntimeException {
        return read(file, DEFAULT_COMMENT, false, false);
    }

    /**
     * Reads a config from the specified path with custom comments and external control flags.
     *
     * @param file              the path to the config file
     * @param comment           the comment header to write to the file if saved
     * @param externallyManaged true if the configuration is controlled externally
     * @param externallyEnabled true if externally enabled
     * @return the loaded DefaultConfig
     * @throws RuntimeException if loading or saving fails
     */
    public static DefaultConfig read(Path file, String[] comment, boolean externallyManaged, boolean externallyEnabled) throws RuntimeException {
        boolean firstRun = !Files.isRegularFile(file);
        Properties properties = readOrEmpty(file);
        AtomicBoolean saveConfig = new AtomicBoolean(firstRun);

        String configVersionStr = properties.getProperty("configVersion");
        int oldConfigVersion = 1;
        if (configVersionStr != null) {
            try {
                oldConfigVersion = Integer.parseInt(configVersionStr.trim());
            } catch (NumberFormatException e) {
                saveConfig.set(true);
            }
        } else {
            oldConfigVersion = firstRun ? 1 : 0;
            saveConfig.set(true);
        }

        if (oldConfigVersion < 1) {
            saveConfig.set(true);
        } else if (oldConfigVersion > 1) {
            saveConfig.set(false);
        }

        UUID serverId;
        String serverIdStr = properties.getProperty("serverId");
        if (serverIdStr != null) {
            try {
                String trimmed = serverIdStr.trim();
                String corrected = trimmed.length() > 36 ? trimmed.substring(0, 36) : trimmed;
                if (!corrected.equals(serverIdStr)) {
                    saveConfig.set(true);
                }
                serverId = UUID.fromString(corrected);
                properties.setProperty("serverId", corrected);
            } catch (IllegalArgumentException e) {
                saveConfig.set(true);
                serverId = UUID.randomUUID();
                properties.setProperty("serverId", serverId.toString());
            }
        } else {
            saveConfig.set(true);
            serverId = UUID.randomUUID();
            properties.setProperty("serverId", serverId.toString());
        }

        boolean enabled = getBooleanProperty(properties, "enabled", true, saveConfig);
        if (externallyManaged) {
            enabled = externallyEnabled;
            properties.remove("enabled");
        } else {
            properties.setProperty("enabled", Boolean.toString(enabled));
        }
        if (firstRun) {
            enabled = false;
        }

        boolean submitMetrics = getBooleanProperty(properties, "submitMetrics", true, saveConfig);
        boolean additionalMetrics = getBooleanProperty(properties, "submitAdditionalMetrics", true, saveConfig);
        boolean debug = getBooleanProperty(properties, "debug", false, saveConfig);

        properties.setProperty("configVersion", "1");

        if (saveConfig.get()) {
            try {
                save(file, comment, properties);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save metrics config", e);
            }
        }

        return new DefaultConfig(file, comment, externallyManaged, properties, firstRun,
                serverId, additionalMetrics, submitMetrics, debug, enabled, oldConfigVersion);
    }

    private static boolean getBooleanProperty(Properties properties, String key, boolean defaultValue, AtomicBoolean saveConfig) {
        String value = properties.getProperty(key);
        if (value == null) {
            saveConfig.set(true);
            properties.setProperty(key, Boolean.toString(defaultValue));
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static Properties readOrEmpty(Path file) throws RuntimeException {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read metrics config", e);
        }
    }

    private static void save(Path file, String[] comment, Properties properties) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (OutputStream out = Files.newOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            String commentStr = comment != null ? String.join("\n", comment) : null;
            properties.store(writer, commentStr);
        }
    }

    @Override
    public UUID getServerId() {
        return serverId;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isSubmitAdditionalMetrics() {
        return additionalMetrics;
    }

    @Override
    public boolean isSubmitMetrics() {
        return submitMetrics;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    @Override
    public boolean isFirstRun() {
        return firstRun;
    }

    @Override
    public void setDefaultProperty(Map<String, String> properties) {
        boolean changed = false;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!this.properties.containsKey(entry.getKey())) {
                this.properties.setProperty(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            try {
                save(file, comment, this.properties);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save default property to config", e);
            }
        }
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    @Override
    public int getOldConfigVersion() {
        return oldConfigVersion;
    }
}
