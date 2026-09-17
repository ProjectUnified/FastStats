package io.github.projectunified.faststats.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MockPlatform implements Platform {
    final MockConfig config = new MockConfig();
    final Collection<Metric<?>> metrics = new ArrayList<>();
    final List<String> loggedInfos = new ArrayList<>();
    final List<String> loggedWarnings = new ArrayList<>();
    final List<String> loggedErrors = new ArrayList<>();

    @Override
    public String getProjectName() {
        return "Mock Project";
    }

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

    @Override
    public void logWarning(String message) {
        loggedWarnings.add(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        loggedErrors.add(message);
    }
}
