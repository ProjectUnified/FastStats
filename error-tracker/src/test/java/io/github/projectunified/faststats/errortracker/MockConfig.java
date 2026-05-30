package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class MockConfig implements Config {
    private final Map<String, String> properties = new HashMap<>();
    private boolean enabled = true;

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public UUID getServerId() {
        return UUID.fromString("12345678-1234-1234-1234-123456789abc");
    }

    @Override
    public boolean isFirstRun() {
        return false;
    }

    @Override
    public boolean isSubmitAdditionalMetrics() {
        return true;
    }

    @Override
    public boolean isDebug() {
        return true;
    }

    @Override
    public void setDefaultProperty(Map<String, String> defaultProperties) {
        defaultProperties.forEach(properties::putIfAbsent);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
}
