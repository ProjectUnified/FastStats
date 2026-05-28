package io.github.projectunified.faststats.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetricsTest {

    @Test
    public void testSubmit_normalFlow() throws Exception {
        MockPlatform platform = new MockPlatform();
        platform.metrics.add(Metric.string("platform_metric", () -> "platform_val"));

        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .addMetric(Metric.number("added_metric", () -> 42))
                .build();

        metrics.submit();

        assertEquals(1, http.callCount);
        assertTrue(http.capturedJson.contains("identifier=12345678-1234-1234-1234-123456789abc"));
        assertTrue(http.capturedJson.contains("platform_metric=platform_val"));
        assertTrue(http.capturedJson.contains("added_metric=42"));
        assertTrue(http.capturedJson.contains("core_count="));
        assertTrue(http.capturedJson.contains("java_vendor="));
        assertTrue(http.capturedJson.contains("java_version="));
        assertTrue(http.capturedJson.contains("os_arch="));
        assertTrue(http.capturedJson.contains("os_name="));
        assertTrue(http.capturedJson.contains("os_version="));
    }

    @Test
    public void testSubmit_disabled() throws Exception {
        MockPlatform platform = new MockPlatform();
        platform.config.enabled = false;

        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .build();

        metrics.submit();

        assertEquals(0, http.callCount);
    }

    @Test
    public void testSubmit_onDemand() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .build();

        Map<String, Object> customPayload = new HashMap<>();
        customPayload.put("custom_key", "custom_val");

        metrics.submit("custom_data", customPayload);

        assertEquals(1, http.callCount);
        assertTrue(http.capturedJson.contains("custom_data={custom_key=custom_val}"));
        assertTrue(http.capturedJson.contains("identifier=12345678-1234-1234-1234-123456789abc"));
    }

    @Test
    public void testSubmit_normalization() throws Exception {
        MockPlatform platform = new MockPlatform();

        // Add map with mixed supported and unsupported types using LinkedHashMap to guarantee order
        Map<String, Object> mixedMap = new LinkedHashMap<>();
        mixedMap.put("num", 100);
        mixedMap.put("bool", true);
        mixedMap.put("custom", new Object() {
            @Override
            public String toString() {
                return "custom_val";
            }
        });
        platform.metrics.add(Metric.map("map_metric", () -> mixedMap));

        // Add collection with mixed types
        Collection<Object> mixedCol = new ArrayList<>();
        mixedCol.add(42);
        mixedCol.add(new Object() {
            @Override
            public String toString() {
                return "col_val";
            }
        });
        platform.metrics.add(Metric.collection("col_metric", () -> mixedCol));

        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .build();

        metrics.submit();

        assertEquals(1, http.callCount);
        assertTrue(http.capturedJson.contains("map_metric={num=100, bool=true, custom=custom_val}"));
        assertTrue(http.capturedJson.contains("col_metric=[42, col_val]"));
    }

    @Test
    public void testSchedule() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .scheduler(scheduler)
                .build();

        metrics.start(1000, 5000);

        assertEquals(1000, scheduler.capturedInitialDelay);
        assertEquals(5000, scheduler.capturedPeriod);

        // Execute task manually via mock scheduler hook
        scheduler.scheduledTask.run();
        assertEquals(1, http.callCount);

        metrics.shutdown();
        assertTrue(scheduler.shutdownCalled);
    }

    @Test
    public void testDefaultTaskScheduler() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .build();

        metrics.start(10, 1000);
        Thread.sleep(100);

        assertTrue(http.callCount >= 1);

        metrics.shutdown();
    }

    @Test
    public void testStart_defaultDelay() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .scheduler(scheduler)
                .build();

        String oldProp = System.getProperty("faststats.initial-delay");
        System.clearProperty("faststats.initial-delay");
        try {
            metrics.start();
            assertEquals(30000L, scheduler.capturedInitialDelay);
            assertEquals(1800000L, scheduler.capturedPeriod);
        } finally {
            if (oldProp != null) {
                System.setProperty("faststats.initial-delay", oldProp);
            }
        }
    }

    @Test
    public void testStart_customDelayProperty() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .scheduler(scheduler)
                .build();

        String oldProp = System.getProperty("faststats.initial-delay");
        System.setProperty("faststats.initial-delay", "45");
        try {
            metrics.start();
            assertEquals(45000L, scheduler.capturedInitialDelay);
            assertEquals(1800000L, scheduler.capturedPeriod);
        } finally {
            if (oldProp != null) {
                System.setProperty("faststats.initial-delay", oldProp);
            } else {
                System.clearProperty("faststats.initial-delay");
            }
        }
    }

    @Test
    public void testStart_firstTimeRun() throws Exception {
        MockPlatform platform = new MockPlatform();
        platform.config.firstRun = true;
        platform.config.enabled = false;

        CapturingHttpExecutor http = new CapturingHttpExecutor();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .httpExecutor(http)
                .scheduler(scheduler)
                .build();

        assertTrue(platform.loggedInfos.isEmpty());
        metrics.start(1000, 5000);
        
        assertFalse(platform.loggedInfos.isEmpty());
        boolean hasOnboardingKeywords = false;
        for (String msg : platform.loggedInfos) {
            if (msg.contains("uses FastStats") || msg.contains("first start with FastStats")) {
                hasOnboardingKeywords = true;
                break;
            }
        }
        assertTrue(hasOnboardingKeywords);
        assertNull(scheduler.scheduledTask);
    }
}
