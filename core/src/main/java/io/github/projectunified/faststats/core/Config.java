package io.github.projectunified.faststats.core;

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


}
