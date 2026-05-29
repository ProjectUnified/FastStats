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
     * Gets the unique key under which this feature's data map is nested in the root payload.
     *
     * @return the feature key
     */
    public abstract String getKey();

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
     * Submits the given map data payload under the feature's key.
     *
     * @param data the map data payload
     * @throws Exception if submission fails
     */
    protected final void submit(Map<String, Object> data) throws Exception {
        if (metrics == null) {
            throw new IllegalStateException("Metrics instance has not been set");
        }
        metrics.submit(getKey(), data);
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
