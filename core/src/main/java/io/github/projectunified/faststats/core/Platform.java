package io.github.projectunified.faststats.core;

import java.util.Collection;

/**
 * Represents the platform environment (e.g. Bukkit, BungeeCord, Velocity, standard JVM)
 * in which the FastStats library is running.
 */
public interface Platform {
    /**
     * Retrieves the platform-specific configuration.
     *
     * @return the platform config instance
     */
    Config getConfig();

    /**
     * Gets default platform-specific metrics.
     *
     * @return a collection of platform metrics
     */
    Collection<Metric<?>> getMetrics();

    /**
     * Logs informational messages.
     *
     * @param message the message to log
     */
    default void logInfo(String message) {
        System.out.println("[FastStats] INFO: " + message);
    }

    /**
     * Logs warning messages.
     *
     * @param message the message to log
     */
    default void logWarning(String message) {
        System.out.println("[FastStats] WARNING: " + message);
    }

    /**
     * Logs error messages.
     *
     * @param message   the error message
     * @param throwable the cause of the error
     */
    default void logError(String message, Throwable throwable) {
        System.err.println("[FastStats] ERROR: " + message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
}
