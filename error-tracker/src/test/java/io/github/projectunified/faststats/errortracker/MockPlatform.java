package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Config;
import io.github.projectunified.faststats.core.Metric;
import io.github.projectunified.faststats.core.Platform;

import java.util.Collection;
import java.util.Collections;

class MockPlatform implements Platform {
    private final MockConfig config = new MockConfig();

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void logInfo(String message) {
    }

    @Override
    public void logWarning(String message) {
    }

    @Override
    public void logError(String message, Throwable throwable) {
    }

    @Override
    public Collection<Metric<?>> getMetrics() {
        return Collections.emptyList();
    }
}
