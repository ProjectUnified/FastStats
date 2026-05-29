package io.github.projectunified.faststats.core;

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
}
