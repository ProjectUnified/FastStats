package io.github.projectunified.faststats.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MockConfig implements Config {
    private final UUID serverId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final Map<String, String> properties = new HashMap<>();
    boolean enabled = true;
    boolean submitAdditionalMetrics = true;
    boolean debug = false;
    boolean firstRun = false;

    @Override
    public UUID getServerId() {
        return serverId;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isSubmitAdditionalMetrics() {
        return submitAdditionalMetrics;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    @Override
    public boolean isFirstRun() {
        return firstRun;
    }

    @Override
    public void setDefaultProperty(Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!this.properties.containsKey(entry.getKey())) {
                this.properties.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
}
