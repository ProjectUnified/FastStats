package io.github.projectunified.faststats.core;

import java.util.*;

/**
 * Main coordinator class that manages configuration, collects metrics, and submits telemetry payloads.
 */
public final class Metrics {
    private final Platform platform;
    private final Serializer serializer;
    private final Submitter submitter;
    private final TaskScheduler scheduler;
    private final List<Metric<?>> additionalMetrics;
    private final List<Feature> features;

    private Metrics(Builder builder) {
        this.platform = builder.platform;
        this.serializer = builder.serializer;
        this.submitter = builder.submitter;
        this.scheduler = builder.scheduler;
        this.additionalMetrics = Collections.unmodifiableList(new ArrayList<>(builder.additionalMetrics));
        this.features = Collections.unmodifiableList(new ArrayList<>(builder.features));
        Map<String, String> defaultProperties = new LinkedHashMap<>();
        for (Feature feature : this.features) {
            feature.setMetrics(this);
            defaultProperties.putAll(feature.getDefaultProperties());
        }
        this.platform.getConfig().setDefaultProperty(defaultProperties);
    }

    /**
     * Instantiates a new Builder.
     *
     * @return the builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the configuration settings.
     *
     * @return the configuration
     */
    public Config getConfig() {
        return platform.getConfig();
    }

    /**
     * Starts the periodic scheduling of metrics submission with default initial delay
     * (30 seconds, configurable via 'faststats.initial-delay' system property)
     * and period (30 minutes).
     */
    public void start() {
        long initialDelayMs = Long.getLong("faststats.initial-delay", 30) * 1000;
        long periodMs = 30 * 60 * 1000;
        start(initialDelayMs, periodMs);
    }

    /**
     * Starts the periodic scheduling of metrics submission.
     *
     * @param initialDelayMs the initial delay in milliseconds
     * @param periodMs       the period between submissions in milliseconds
     */
    public void start(long initialDelayMs, long periodMs) {
        if (scheduler == null) {
            throw new IllegalStateException("TaskScheduler must be specified to start scheduling");
        }
        Config config = platform.getConfig();
        if (config.isFirstRun()) {
            String[] onboardingMessage = {
                    "This plugin uses FastStats to collect anonymous usage statistics.",
                    "No personal or identifying information is ever collected.",
                    "To opt out, set 'enabled=false' in the metrics configuration file.",
                    "Learn more at: https://faststats.dev/info",
                    "",
                    "Since this is your first start with FastStats, metrics submission will not start",
                    "until you restart the server to allow you to opt out if you prefer."
            };

            int separatorLength = 0;
            for (String s : onboardingMessage) {
                if (s.length() > separatorLength) {
                    separatorLength = s.length();
                }
            }

            StringBuilder separatorBuilder = new StringBuilder();
            for (int i = 0; i < separatorLength; i++) {
                separatorBuilder.append("-");
            }
            String separator = separatorBuilder.toString();

            platform.logInfo(separator);
            for (String s : onboardingMessage) {
                platform.logInfo(s);
            }
            platform.logInfo(separator);
        }
        if (!config.isEnabled()) {
            logInfo("Metrics disabled, not starting submission");
            return;
        }
        scheduler.schedule(() -> {
            try {
                submit();
            } catch (Throwable t) {
                logError("Error during scheduled metrics submission", t);
            }
        }, initialDelayMs, periodMs);

        for (Feature feature : features) {
            try {
                feature.onStart();
            } catch (Throwable t) {
                logError("Error starting feature " + feature.getKey(), t);
            }
        }
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        for (Feature feature : features) {
            try {
                feature.onShutdown();
            } catch (Throwable t) {
                logError("Error shutting down feature " + feature.getKey(), t);
            }
        }
    }

    /**
     * Automatically gathers system/platform/additional metrics and submits them.
     *
     * @throws Exception if data collection or submission fails
     */
    void submit() throws Exception {
        Config config = platform.getConfig();
        if (!config.isEnabled()) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("core_count", Runtime.getRuntime().availableProcessors());
        data.put("java_vendor", System.getProperty("java.vendor"));
        data.put("java_version", System.getProperty("java.version"));
        data.put("os_arch", System.getProperty("os.arch"));
        data.put("os_name", System.getProperty("os.name"));
        data.put("os_version", System.getProperty("os.version"));

        Collection<Metric<?>> platformMetrics = platform.getMetrics();
        if (platformMetrics != null) {
            for (Metric<?> metric : platformMetrics) {
                try {
                    Object val = metric.getValue();
                    if (val != null) {
                        data.put(metric.getName(), val);
                    }
                } catch (Exception e) {
                    logError("Failed to collect platform metric " + metric.getName(), e);
                }
            }
        }

        if (config.isSubmitAdditionalMetrics()) {
            for (Metric<?> metric : additionalMetrics) {
                try {
                    Object val = metric.getValue();
                    if (val != null) {
                        data.put(metric.getName(), val);
                    }
                } catch (Exception e) {
                    logError("Failed to collect metric " + metric.getName(), e);
                }
            }
        }

        submit("data", data);
    }

    /**
     * Directly serializes and submits the given map payload.
     * Injects the server identifier automatically if missing.
     *
     * @param key  the key under which the data map is nested in the root payload
     * @param data the map data payload
     * @throws Exception if submission fails
     */
    void submit(String key, Map<String, Object> data) throws Exception {
        Config config = platform.getConfig();
        if (!config.isEnabled()) {
            logInfo("Metrics submission is disabled.");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identifier", config.getServerId().toString());
        payload.put(key, data);

        String json = serializer.serialize(payload);
        logInfo("Submitting metrics payload: " + json);
        try {
            submitter.execute(json);
            logInfo("Metrics submitted successfully.");
        } catch (Exception e) {
            logError("Failed to submit metrics", e);
            throw e;
        }
    }

    private void logInfo(String message) {
        if (platform.getConfig().isDebug()) {
            platform.logInfo(message);
        }
    }

    private void logWarning(String message) {
        if (platform.getConfig().isDebug()) {
            platform.logWarning(message);
        }
    }

    private void logError(String message, Throwable throwable) {
        if (platform.getConfig().isDebug()) {
            platform.logError(message, throwable);
        }
    }

    /**
     * Builder class for {@link Metrics}.
     */
    public static final class Builder {
        private final List<Metric<?>> additionalMetrics = new ArrayList<>();
        private final List<Feature> features = new ArrayList<>();
        private Platform platform;
        private Serializer serializer;
        private Submitter submitter;
        private TaskScheduler scheduler = TaskScheduler.defaultScheduler();

        /**
         * Sets the platform implementation.
         *
         * @param platform the platform
         * @return this builder instance
         */
        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        /**
         * Sets the JSON serializer.
         *
         * @param serializer the serializer
         * @return this builder instance
         */
        public Builder serializer(Serializer serializer) {
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the submitter.
         *
         * @param submitter the submitter
         * @return this builder instance
         */
        public Builder submitter(Submitter submitter) {
            this.submitter = submitter;
            return this;
        }

        /**
         * Sets the task scheduler.
         *
         * @param scheduler the task scheduler
         * @return this builder instance
         */
        public Builder scheduler(TaskScheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Adds a metric to be collected and submitted.
         *
         * @param metric the metric to add
         * @return this builder instance
         */
        public Builder addMetric(Metric<?> metric) {
            this.additionalMetrics.add(metric);
            return this;
        }

        /**
         * Adds a collection of metrics to be collected and submitted.
         *
         * @param metrics the metrics to add
         * @return this builder instance
         */
        public Builder addMetrics(Collection<Metric<?>> metrics) {
            this.additionalMetrics.addAll(metrics);
            return this;
        }

        /**
         * Adds a feature to be configured with the submit executor.
         *
         * @param feature the feature to add
         * @return this builder instance
         */
        public Builder addFeature(Feature feature) {
            this.features.add(feature);
            return this;
        }

        /**
         * Adds a collection of features to be configured with the submit executor.
         *
         * @param features the features to add
         * @return this builder instance
         */
        public Builder addFeatures(Collection<Feature> features) {
            this.features.addAll(features);
            return this;
        }

        /**
         * Builds the {@link Metrics} instance.
         *
         * @return a new Metrics instance
         * @throws IllegalStateException if any required fields are missing
         */
        public Metrics build() {
            if (platform == null) {
                throw new IllegalStateException("Platform must be specified");
            }
            if (serializer == null) {
                throw new IllegalStateException("Serializer must be specified");
            }
            if (submitter == null) {
                throw new IllegalStateException("Submitter must be specified");
            }
            return new Metrics(this);
        }
    }
}
