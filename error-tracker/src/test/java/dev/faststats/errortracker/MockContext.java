package dev.faststats.errortracker;

import com.google.gson.JsonObject;
import dev.faststats.Config;
import dev.faststats.FastStatsService;
import dev.faststats.Metrics;
import dev.faststats.SimpleContext;
import dev.faststats.Token;
import dev.faststats.internal.Logger;
import dev.faststats.internal.PlatformLoggerFactory;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class MockContext {
    private MockContext() {
    }

    private record MockConfig(
            UUID serverId,
            boolean enabled,
            boolean submitMetrics,
            boolean errorTracking,
            boolean additionalMetrics,
            boolean debug
    ) implements Config {
    }

    public static final class Factory {
        private final SimpleContext.Builder builder = new SimpleContext.Builder("test");
        private Config config = new MockConfig(UUID.randomUUID(), true, true, true, true, true);
        private boolean firstRun = false;
        @Token
        private @Nullable String token = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        public Factory noToken() {
            token = null;
            return this;
        }

        public Factory allFeaturesDisabled() {
            config = new MockConfig(config.serverId(), false, false, false, false, false);
            return this;
        }

        public Factory firstRun() {
            firstRun = true;
            return this;
        }

        public Factory metrics(final Function<Metrics.Factory, Metrics> metrics) {
            builder.metrics(metrics);
            return this;
        }

        public Factory defaultMetrics(final BiConsumer<SimpleContext, JsonObject> defaultMetrics) {
            builder.defaultMetrics(defaultMetrics);
            return this;
        }

        public Factory service(final Function<SimpleContext, ? extends FastStatsService> service) {
            builder.service(service);
            return this;
        }

        public Factory errorTracker(final ErrorTracker errorTracker) {
            builder.service(ErrorTrackerService.create(errorTracker));
            return this;
        }

        public SimpleContext create() {
            final var context = builder
                    .loggerFactory(new PlatformLoggerFactory((level, throwable, message) -> {
                        final var output = level == Logger.LogLevel.ERROR ? System.err : System.out;
                        output.println("[" + level.name() + "] " + message);
                        if (throwable != null) throwable.printStackTrace(output);
                    }))
                    .config(config)
                    .projectName("Mock")
                    .token(token)
                    .tokenResource(MockContext.class.getClassLoader(), "mock")
                    .build();
            // mirrors platforms that start submission right away, unless it is the first run
            if (!firstRun) context.startSubmission();
            return context;
        }
    }
}
