package io.github.projectunified.faststats.core;

import java.util.Map;
import java.util.UUID;

/**
 * Interface representing the configuration settings for metrics collection.
 */
public interface Config {
    /**
     * Retrieves the unique identifier of the server.
     *
     * @return the server identifier UUID
     */
    UUID getServerId();

    /**
     * Checks if metrics submission is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Checks if additional/custom metrics are enabled for submission.
     *
     * @return true if enabled, false otherwise
     */
    boolean isSubmitAdditionalMetrics();

    /**
     * Checks if metrics submission is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isSubmitMetrics();

    /**
     * Checks if debug logging is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isDebug();

    /**
     * Checks if this is the first time the metrics are running.
     *
     * @return true if first run, false otherwise
     */
    default boolean isFirstRun() {
        return false;
    }


    /**
     * Sets default properties.
     *
     * @param properties the default properties map
     */
    void setDefaultProperty(Map<String, String> properties);

    /**
     * Gets a property value.
     *
     * @param key          the key
     * @param defaultValue the default value to return if not present
     * @return the property value or the default value
     */
    String getProperty(String key, String defaultValue);

    /**
     * Retrieves the version of the configuration file.
     *
     * @return the configuration version
     */
    default int getConfigVersion() {
        return 1;
    }

    /**
     * Retrieves the previous version of the configuration file before loading/updating.
     * If the configuration file was not upgraded, it will be equal to {@link #getConfigVersion()}.
     *
     * @return the previous configuration version, or {@link #getConfigVersion()} if not updated
     */
    default int getOldConfigVersion() {
        return getConfigVersion();
    }
}
