package io.github.projectunified.faststats.core;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * An abstract representation of a feature that can submit telemetry payloads on demand.
 */
public abstract class Feature {
    private Metrics metrics;

    /**
     * Called when the Metrics coordinator is started.
     * Subclasses can override this to initialize scheduling or start listeners.
     */
    public void onStart() {
    }

    /**
     * Called when the Metrics coordinator is shutdown.
     * Subclasses can override this to release resources or stop listeners.
     */
    public void onShutdown() {
    }

    /**
     * Gets the default properties to assign to the configuration.
     *
     * @return the default properties
     */
    public Map<String, String> getDefaultProperties() {
        return Collections.emptyMap();
    }

    /**
     * Gets the Metrics instance.
     *
     * @return the Metrics instance
     */
    private Metrics getMetrics() {
        if (metrics == null) {
            throw new IllegalStateException("Metrics instance has not been set");
        }
        return metrics;
    }

    /**
     * Sets the Metrics instance. This method is package-private so it can only be called from within the library.
     *
     * @param metrics the Metrics instance
     */
    final void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Called when the configuration needs migration.
     *
     * @param config           the configuration
     * @param oldConfigVersion the old configuration version
     * @param newConfigVersion the new configuration version
     */
    public void onConfigMigrate(Config config, int oldConfigVersion, int newConfigVersion) {
    }

    /**
     * Submits the given data map payload with a custom path and context key.
     *
     * @param path       the target path or URL
     * @param dataMap    a map of keys to their data maps
     * @param contextKey the key to nest platform and default metrics data under, or null to omit
     * @throws Exception if submission fails
     */
    protected final void submit(String path, Map<String, Object> dataMap, String contextKey) throws Exception {
        getMetrics().submit(path, dataMap, contextKey);
    }

    /**
     * Gets a configuration property.
     *
     * @param key          the property key
     * @param defaultValue the default value to return if the property is missing
     * @return the property value, or the default value if metrics is not initialized
     */
    public final String getProperty(String key, String defaultValue) {
        return getMetrics().getConfig().getProperty(key, defaultValue);
    }

    /**
     * Finds a registered feature of the specified class.
     *
     * @param featureClass the feature class
     * @param <T>          the feature type
     * @return an Optional containing the feature if found, or empty if not found or metrics is not set
     */
    public final <T extends Feature> Optional<T> getFeature(Class<T> featureClass) {
        return getMetrics().getFeature(featureClass);
    }

    /**
     * Gets the TaskScheduler instance.
     *
     * @return the TaskScheduler instance
     */
    protected final TaskScheduler getScheduler() {
        return getMetrics().getScheduler();
    }
}
