package io.github.projectunified.faststats.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link Config} that reads and writes configurations
 * from a properties file.
 */
public class DefaultConfig implements Config {
    private final UUID serverId;
    private final boolean additionalMetrics;
    private final boolean debug;
    private final boolean enabled;
    private final boolean errorTracking;
    private final boolean firstRun;
    private final boolean externallyManaged;

    /**
     * Constructs a new {@link DefaultConfig} instance.
     *
     * @param serverId          the server ID
     * @param additionalMetrics whether to submit additional metrics
     * @param debug             whether debug logging is enabled
     * @param enabled           whether metrics collection is enabled
     * @param errorTracking     whether to track errors
     * @param firstRun          whether it is the first time running stats
     * @param externallyManaged whether config is externally managed
     */
    public DefaultConfig(UUID serverId, boolean additionalMetrics, boolean debug, boolean enabled,
                         boolean errorTracking, boolean firstRun, boolean externallyManaged) {
        this.serverId = serverId;
        this.additionalMetrics = additionalMetrics;
        this.debug = debug;
        this.enabled = enabled;
        this.errorTracking = errorTracking;
        this.firstRun = firstRun;
        this.externallyManaged = externallyManaged;
    }

    public static final String DEFAULT_COMMENT =
            " FastStats (https://faststats.dev) collects anonymous usage statistics for plugin developers.\n" +
            "# This helps developers understand how their projects are used in the real world.\n" +
            "#\n" +
            "# No IP addresses, player data, or personal information is collected.\n" +
            "# The server ID below is randomly generated and can be regenerated at any time.\n" +
            "#\n" +
            "# Enabling metrics has no noticeable performance impact.\n" +
            "# Keeping metrics enabled is recommended, but you can opt out by setting\n" +
            "# 'enabled=false' in plugins/faststats/config.properties.\n" +
            "#\n" +
            "# If you suspect a plugin is collecting personal data or bypassing the \"enabled\" option,\n" +
            "# please report it at: https://faststats.dev/abuse\n" +
            "#\n" +
            "# For more information, visit: https://faststats.dev/info\n";

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
    public static DefaultConfig read(Path file, String comment, boolean externallyManaged, boolean externallyEnabled) throws RuntimeException {
        boolean firstRun = !Files.isRegularFile(file);
        Properties properties = readOrEmpty(file);
        AtomicBoolean saveConfig = new AtomicBoolean(firstRun);

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
            } catch (IllegalArgumentException e) {
                saveConfig.set(true);
                serverId = UUID.randomUUID();
            }
        } else {
            saveConfig.set(true);
            serverId = UUID.randomUUID();
        }

        boolean enabled = externallyManaged ? externallyEnabled : getBooleanProperty(properties, "enabled", !firstRun, saveConfig);
        boolean errorTracking = getBooleanProperty(properties, "submitErrors", true, saveConfig);
        boolean additionalMetrics = getBooleanProperty(properties, "submitAdditionalMetrics", true, saveConfig);
        boolean debug = getBooleanProperty(properties, "debug", false, saveConfig);

        if (saveConfig.get()) {
            try {
                save(file, externallyManaged, comment, serverId, enabled, errorTracking, additionalMetrics, debug);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save metrics config", e);
            }
        }

        return new DefaultConfig(serverId, additionalMetrics, debug, enabled, errorTracking, firstRun, externallyManaged);
    }

    private static boolean getBooleanProperty(Properties properties, String key, boolean defaultValue, AtomicBoolean saveConfig) {
        String value = properties.getProperty(key);
        if (value == null) {
            saveConfig.set(true);
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

    private static void save(Path file, boolean externallyManaged, String comment, UUID serverId, boolean enabled, boolean errorTracking, boolean additionalMetrics, boolean debug) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (OutputStream out = Files.newOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();

            properties.setProperty("serverId", serverId.toString());
            if (!externallyManaged) {
                properties.setProperty("enabled", Boolean.toString(enabled));
            }
            properties.setProperty("submitErrors", Boolean.toString(errorTracking));
            properties.setProperty("submitAdditionalMetrics", Boolean.toString(additionalMetrics));
            properties.setProperty("debug", Boolean.toString(debug));

            properties.store(writer, comment);
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
    public boolean isDebug() {
        return debug;
    }

    /**
     * Checks if error tracking is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isErrorTracking() {
        return errorTracking;
    }

    /**
     * Helper method to check if it's the first time running the stats.
     *
     * @return true if first time, false otherwise
     */
    @Override
    public boolean isFirstRun() {
        return firstRun;
    }

    /**
     * Checks if the configuration is externally managed.
     *
     * @return true if externally managed, false otherwise
     */
    @Override
    public boolean isExternallyManaged() {
        return externallyManaged;
    }
}
