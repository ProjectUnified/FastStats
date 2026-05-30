package io.github.projectunified.faststats.core;

import java.util.Collections;
import java.util.Map;

/**
 * An abstract representation of a feature that can submit telemetry payloads on demand.
 */
public abstract class Feature {
    private Metrics metrics;

    /**
     * Sets the Metrics instance. This method is package-private so it can only be called from within the library.
     *
     * @param metrics the Metrics instance
     */
    final void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

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
     * Submits the given data map payload.
     *
     * @param dataMap a map of keys to their data maps
     * @throws Exception if submission fails
     */
    protected final void submit(Map<String, Map<String, Object>> dataMap) throws Exception {
        if (metrics == null) {
            throw new IllegalStateException("Metrics instance has not been set");
        }
        metrics.submit(dataMap);
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
     * Gets a configuration property.
     *
     * @param key          the property key
     * @param defaultValue the default value to return if the property is missing
     * @return the property value, or the default value if metrics is not initialized
     */
    public final String getProperty(String key, String defaultValue) {
        if (metrics == null) {
            return defaultValue;
        }
        return metrics.getConfig().getProperty(key, defaultValue);
    }
}
