package io.github.projectunified.faststats.core;

import java.util.*;

/**
 * Main coordinator class that manages configuration, collects metrics, and submits telemetry payloads.
 */
public class Metrics {
    private final Platform platform;
    private final JsonSerializer serializer;
    private final HttpExecutor httpExecutor;
    private final TaskScheduler scheduler;
    private final List<Metric<?>> additionalMetrics;

    private Metrics(Builder builder) {
        this.platform = builder.platform;
        this.serializer = builder.serializer;
        this.httpExecutor = builder.httpExecutor;
        this.scheduler = builder.scheduler;
        this.additionalMetrics = Collections.unmodifiableList(new ArrayList<>(builder.additionalMetrics));
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
            String onboardingMessage =
                    "This plugin uses FastStats to collect anonymous usage statistics.\n" +
                    "No personal or identifying information is ever collected.\n" +
                    "To opt out, set 'enabled=false' in the metrics configuration file.\n" +
                    "Learn more at: https://faststats.dev/info\n\n" +
                    "Since this is your first start with FastStats, metrics submission will not start\n" +
                    "until you restart the server to allow you to opt out if you prefer.";

            int separatorLength = 0;
            String[] split = onboardingMessage.split("\n");
            for (String s : split) {
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
            for (String s : split) {
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
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Automatically gathers system/platform/additional metrics and submits them.
     *
     * @throws Exception if data collection or submission fails
     */
    public void submit() throws Exception {
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
    public void submit(String key, Map<String, Object> data) throws Exception {
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
            httpExecutor.execute(json);
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
    public static class Builder {
        private final List<Metric<?>> additionalMetrics = new ArrayList<>();
        private Platform platform;
        private JsonSerializer serializer;
        private HttpExecutor httpExecutor;
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
         * @param serializer the JSON serializer
         * @return this builder instance
         */
        public Builder serializer(JsonSerializer serializer) {
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the HTTP executor.
         *
         * @param httpExecutor the HTTP executor
         * @return this builder instance
         */
        public Builder httpExecutor(HttpExecutor httpExecutor) {
            this.httpExecutor = httpExecutor;
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
                throw new IllegalStateException("JsonSerializer must be specified");
            }
            if (httpExecutor == null) {
                throw new IllegalStateException("HttpExecutor must be specified");
            }
            return new Metrics(this);
        }
    }
}
