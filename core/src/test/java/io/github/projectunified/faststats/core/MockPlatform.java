package io.github.projectunified.faststats.core;

import java.util.ArrayList;
import java.util.Collection;

public class MockPlatform implements Platform {
    final MockConfig config = new MockConfig();
    final Collection<Metric<?>> metrics = new ArrayList<>();
    final java.util.List<String> loggedInfos = new ArrayList<>();

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public Collection<Metric<?>> getMetrics() {
        return metrics;
    }

    @Override
    public void logInfo(String message) {
        loggedInfos.add(message);
    }
}
