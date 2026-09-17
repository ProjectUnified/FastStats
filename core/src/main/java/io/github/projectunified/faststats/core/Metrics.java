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
    private final List<Runnable> flushes;

    private Metrics(Builder builder) {
        this.platform = builder.platform;
        this.serializer = builder.serializer;
        this.submitter = builder.submitter;
        this.scheduler = builder.scheduler;
        this.additionalMetrics = Collections.unmodifiableList(new ArrayList<>(builder.additionalMetrics));
        this.features = Collections.unmodifiableList(new ArrayList<>(builder.features));
        this.flushes = Collections.unmodifiableList(new ArrayList<>(builder.flushes));
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
     * Gets the TaskScheduler instance.
     *
     * @return the TaskScheduler instance
     */
    TaskScheduler getScheduler() {
        return scheduler;
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
     * Finds a registered feature of the specified class.
     *
     * @param featureClass the feature class
     * @param <T>          the feature type
     * @return an Optional containing the feature if found, or empty otherwise
     */
    public <T extends Feature> Optional<T> getFeature(Class<T> featureClass) {
        for (Feature feature : features) {
            if (featureClass.isInstance(feature)) {
                return Optional.of(featureClass.cast(feature));
            }
        }
        return Optional.empty();
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
                    "This plugin uses FastStats to collect pseudonymous usage statistics and errors.",
                    "No personal or identifying information is ever collected.",
                    "To opt out, set 'enabled=false' in the metrics configuration file.",
                    "Learn more at: https://faststats.dev/info",
                    "",
                    "Since this is your first start with FastStats, submission will not start",
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
        int oldVersion = config.getOldConfigVersion();
        int currentVersion = config.getConfigVersion();
        if (oldVersion < currentVersion) {
            for (Feature feature : features) {
                try {
                    feature.onConfigMigrate(config, oldVersion, currentVersion);
                } catch (Throwable t) {
                    logError("Error migrating config for feature " + feature.getClass().getSimpleName(), t);
                }
            }
        }
        if (config.isSubmitMetrics()) {
            scheduler.schedule(() -> {
                try {
                    submitMetricsPayload();
                } catch (Throwable t) {
                    logError("Error during scheduled metrics submission", t);
                }
            }, initialDelayMs, periodMs);
        } else {
            logInfo("Metrics submission is disabled, not scheduling submission");
        }

        for (Feature feature : features) {
            try {
                feature.onStart();
            } catch (Throwable t) {
                logError("Error starting feature " + feature.getClass().getSimpleName(), t);
            }
        }
    }

    /**
     * Shuts down the scheduler and submits the last metrics payload.
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        Config config = platform.getConfig();
        if (config.isEnabled() && config.isSubmitMetrics()) {
            try {
                submitMetricsPayload();
            } catch (Throwable t) {
                logError("Error during metrics submission on shutdown", t);
            }
        }
        for (Feature feature : features) {
            try {
                feature.onShutdown();
            } catch (Throwable t) {
                logError("Error shutting down feature " + feature.getClass().getSimpleName(), t);
            }
        }
    }

    /**
     * Submits the metrics payload and runs the configured flush callbacks
     * if the payload was accepted by the server.
     *
     * @throws Exception if submission fails
     */
    private void submitMetricsPayload() throws Exception {
        Map<String, Object> payload = createMetricsPayload();
        if (payload.isEmpty()) {
            return;
        }
        Submitter.Response response = submit("/v1/collect", payload, true);
        if (!response.isSuccessful()) {
            return;
        }
        for (Runnable flush : flushes) {
            try {
                flush.run();
            } catch (Throwable t) {
                logError("Error running flush callback", t);
            }
        }
    }

    /**
     * Creates the payload submitted to the metrics collection endpoint.
     *
     * @return the metrics payload, or an empty map if no metrics are collected
     */
    private Map<String, Object> createMetricsPayload() {
        Map<String, Object> data = getDefaultContext();
        if (data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project_name", platform.getProjectName());
        payload.put("data", data);
        return payload;
    }

    /**
     * Gets the name of the project reporting metrics.
     *
     * @return the project name
     */
    String getProjectName() {
        return platform.getProjectName();
    }

    /**
     * Gets the default context containing OS and platform information.
     *
     * @return the default context, or an empty map if metrics submission is disabled
     */
    Map<String, Object> getDefaultContext() {
        Config config = platform.getConfig();
        if (!config.isSubmitMetrics()) {
            return Collections.emptyMap();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("client", false);
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
                    if (val == null) {
                        continue;
                    }
                    if (data.containsKey(metric.getName())) {
                        platform.logWarning("Skipped duplicated metrics entry: " + metric.getName());
                        continue;
                    }
                    data.put(metric.getName(), val);
                } catch (Exception e) {
                    logError("Failed to collect metric " + metric.getName(), e);
                }
            }
        }
        return data;
    }

    Submitter.Response submit(String path, Map<String, Object> dataMap, boolean compressed) throws Exception {
        Config config = platform.getConfig();
        if (!config.isEnabled()) {
            logInfo("Submission is disabled.");
            throw new IllegalStateException("Metrics system is disabled");
        }
        if (dataMap.isEmpty()) {
            return Submitter.Response.create(200, null, null);
        }

        Map<String, Object> payload = new LinkedHashMap<>(dataMap);
        payload.put("identifier", config.getServerId().toString());

        String json = serializer.serialize(payload);
        logInfo("Submitting payload: " + json);
        try {
            Submitter.Response response = submitter.execute(path, json, compressed);
            logResponse(path, response);
            return response;
        } catch (Exception e) {
            logError("Failed to submit/execute request", e);
            throw e;
        }
    }

    /**
     * Logs the outcome of a submission. Failures are always logged, successful
     * submissions are only logged in debug mode.
     *
     * @param path     the target path or URL
     * @param response the response to log
     */
    private void logResponse(String path, Submitter.Response response) {
        if (response.getException().isPresent()) {
            platform.logError("Failed to submit to " + path, response.getException().get());
            return;
        }

        int statusCode = response.getStatusCode();
        if (response.isSuccessful()) {
            String body = readBody(response);
            if (hasWarnings(body)) {
                platform.logWarning("Submitted to " + path + " with warnings: " + body);
            } else {
                logInfo("Submitted to " + path + " successfully with status code " + statusCode);
            }
        } else if (statusCode >= 300 && statusCode < 400) {
            platform.logWarning("Received redirect response from " + path + ": " + statusCode + " (" + readBody(response) + ")");
        } else if (statusCode >= 400 && statusCode < 500) {
            platform.logError("Submitted invalid request to " + path + ": " + statusCode + " (" + readBody(response) + ")", null);
        } else if (statusCode >= 500 && statusCode < 600) {
            platform.logError("Received server error response from " + path + ": " + statusCode + " (" + readBody(response) + ")", null);
        } else {
            platform.logWarning("Received unexpected response from " + path + ": " + statusCode + " (" + readBody(response) + ")");
        }
    }

    private boolean hasWarnings(String body) {
        if (body.isEmpty()) {
            return false;
        }
        try {
            return deserialize(body).containsKey("warnings");
        } catch (Exception e) {
            return false;
        }
    }

    private String readBody(Submitter.Response response) {
        try {
            return response.readString();
        } catch (Exception e) {
            return "";
        }
    }

    Map<String, Object> deserialize(String json) throws Exception {
        return serializer.deserialize(json);
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
        private final List<Runnable> flushes = new ArrayList<>();
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
         * @throws IllegalArgumentException if the metric name is invalid or already added
         */
        public Builder addMetric(Metric<?> metric) {
            String name = metric.getName();
            if (name == null || !name.matches(SimpleMetric.NAME_PATTERN)) {
                throw new IllegalArgumentException("Invalid metric name '" + name + "', must match '" + SimpleMetric.NAME_PATTERN + "'");
            }
            for (Metric<?> existing : additionalMetrics) {
                if (name.equals(existing.getName())) {
                    throw new IllegalArgumentException("Metric already added: " + name);
                }
            }
            this.additionalMetrics.add(metric);
            return this;
        }

        /**
         * Adds a collection of metrics to be collected and submitted.
         *
         * @param metrics the metrics to add
         * @return this builder instance
         * @throws IllegalArgumentException if a metric name is invalid or already added
         */
        public Builder addMetrics(Collection<Metric<?>> metrics) {
            for (Metric<?> metric : metrics) {
                addMetric(metric);
            }
            return this;
        }

        /**
         * Adds a flush callback to this metrics instance.
         * <p>
         * The callback is invoked after the metrics payload has been accepted by
         * the metrics server, which makes it suitable for resetting counters that
         * accumulate between submissions.
         *
         * @param flush the flush callback
         * @return this builder instance
         */
        public Builder onFlush(Runnable flush) {
            if (flush != null) {
                this.flushes.add(flush);
            }
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
