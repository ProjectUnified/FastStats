package io.github.projectunified.faststats.core;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsTest {

    @Test
    public void testSubmit_normalFlow() throws Exception {
        MockPlatform platform = new MockPlatform();
        platform.metrics.add(Metric.string("platform_metric", () -> "platform_val"));

        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .addMetric(Metric.number("added_metric", () -> 42))
                .build();

        metrics.submit("/v1/collect", Collections.singletonMap("data", metrics.getDefaultContext()), false);

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
    public void testCollectPayload() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(new SimpleSerializer())
                .submitter(http)
                .scheduler(scheduler)
                .build();

        metrics.start(1000, 5000);
        scheduler.scheduledTask.run();

        assertEquals(1, http.callCount);
        assertEquals("/v1/collect", http.capturedPath);
        assertTrue(http.capturedJson.contains("project_name=Mock Project"));
        assertTrue(http.capturedJson.contains("client=false"));
        assertTrue(http.capturedJson.contains("identifier=12345678-1234-1234-1234-123456789abc"));
    }

    @Test
    public void testShutdown_submitsFinalMetrics() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(new SimpleSerializer())
                .submitter(http)
                .scheduler(scheduler)
                .build();

        metrics.start(1000, 5000);
        assertEquals(0, http.callCount);

        metrics.shutdown();
        assertTrue(scheduler.shutdownCalled);
        assertEquals(1, http.callCount);
        assertEquals("/v1/collect", http.capturedPath);
        assertTrue(http.capturedJson.contains("project_name=Mock Project"));

        // Nothing is submitted when the user opted out of metrics submission
        MockPlatform disabledPlatform = new MockPlatform();
        disabledPlatform.config.submitMetrics = false;
        CapturingSubmitter disabledHttp = new CapturingSubmitter();

        Metrics disabledMetrics = Metrics.builder()
                .platform(disabledPlatform)
                .serializer(new SimpleSerializer())
                .submitter(disabledHttp)
                .scheduler(new MockTaskScheduler())
                .build();

        disabledMetrics.shutdown();
        assertEquals(0, disabledHttp.callCount);
    }

    @Test
    public void testSubmit_reportsUnsuccessfulResponse() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .build();

        http.statusCode = 500;
        metrics.submit("/v1/collect", Collections.singletonMap("data", metrics.getDefaultContext()), true);

        assertEquals(1, http.callCount);
        assertEquals(1, platform.loggedErrors.size());
        assertTrue(platform.loggedErrors.get(0).contains("500"));
        assertTrue(platform.loggedWarnings.isEmpty());

        platform.loggedErrors.clear();
        http.statusCode = 200;
        http.response = "{warnings=[duplicate metric]}";
        metrics.submit("/v1/collect", Collections.singletonMap("data", metrics.getDefaultContext()), true);

        assertTrue(platform.loggedErrors.isEmpty());
        assertEquals(1, platform.loggedWarnings.size());
        assertTrue(platform.loggedWarnings.get(0).contains("warnings"));
    }

    @Test
    public void testMetricNameValidation() {
        assertThrows(IllegalArgumentException.class, () -> Metric.string("Invalid Metric", () -> "value"));
        assertThrows(IllegalArgumentException.class, () -> Metric.number("camelCase", () -> 1));
        assertNotNull(Metric.string("valid_name_2", () -> "value"));

        Metrics.Builder builder = Metrics.builder()
                .platform(new MockPlatform())
                .serializer(new SimpleSerializer())
                .submitter(new CapturingSubmitter())
                .addMetric(Metric.string("duplicate_metric", () -> "a"));

        assertThrows(IllegalArgumentException.class, () -> builder.addMetric(Metric.number("duplicate_metric", () -> 1)));
    }

    @Test
    public void testDuplicateMetricEntrySkipped() throws Exception {
        MockPlatform platform = new MockPlatform();
        platform.metrics.add(Metric.string("shared_metric", () -> "platform_value"));

        CapturingSubmitter http = new CapturingSubmitter();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(new SimpleSerializer())
                .submitter(http)
                .addMetric(Metric.string("shared_metric", () -> "custom_value"))
                .build();

        metrics.submit("/v1/collect", Collections.singletonMap("data", metrics.getDefaultContext()), true);

        assertTrue(http.capturedJson.contains("shared_metric=platform_value"));
        assertFalse(http.capturedJson.contains("custom_value"));
        assertEquals(1, platform.loggedWarnings.size());
    }

    @Test
    public void testFlushCallback_afterSuccessfulSubmission() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        MockTaskScheduler scheduler = new MockTaskScheduler();
        AtomicInteger flushes = new AtomicInteger();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(new SimpleSerializer())
                .submitter(http)
                .scheduler(scheduler)
                .onFlush(flushes::incrementAndGet)
                .build();

        metrics.start(1000, 5000);
        scheduler.scheduledTask.run();

        assertEquals(1, http.callCount);
        assertEquals(1, flushes.get());

        http.statusCode = 500;
        scheduler.scheduledTask.run();

        assertEquals(2, http.callCount);
        assertEquals(1, flushes.get());
    }

    @Test
    public void testSubmit_disabled() throws Exception {
        MockPlatform platform = new MockPlatform();
        platform.config.enabled = false;

        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .build();

        assertThrows(IllegalStateException.class, () -> {
            metrics.submit("/v1/collect", Collections.singletonMap("data", metrics.getDefaultContext()), false);
        });

        assertEquals(0, http.callCount);
    }

    @Test
    public void testSubmit_onDemand() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .build();

        Map<String, Object> customPayload = new HashMap<>();
        customPayload.put("custom_key", "custom_val");

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("custom_data", customPayload);
        metrics.submit("/v1/collect", dataMap, false);

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

        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .build();

        metrics.submit("/v1/collect", Collections.singletonMap("data", metrics.getDefaultContext()), false);

        assertEquals(1, http.callCount);
        assertTrue(http.capturedJson.contains("map_metric={num=100, bool=true, custom=custom_val}"));
        assertTrue(http.capturedJson.contains("col_metric=[42, col_val]"));
    }

    @Test
    public void testSchedule() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
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
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .build();

        metrics.start(10, 1000);
        Thread.sleep(100);

        assertTrue(http.callCount >= 1);

        metrics.shutdown();
    }

    @Test
    public void testStart_defaultDelay() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
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
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
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

        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
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

    @Test
    public void testFeatureSubmit() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        class MyFeature extends Feature {
            boolean started = false;
            boolean shutdown = false;

            @Override
            public void onStart() {
                started = true;
            }

            @Override
            public void onShutdown() {
                shutdown = true;
            }

            public void triggerSubmit(Map<String, Object> dataMap) throws Exception {
                submit("/v1/collect", dataMap, false);
            }
        }

        MyFeature myFeature = new MyFeature();

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .scheduler(scheduler)
                .addFeature(myFeature)
                .build();

        assertFalse(myFeature.started);
        assertFalse(myFeature.shutdown);

        metrics.start(1000, 5000);
        assertTrue(myFeature.started);
        assertFalse(myFeature.shutdown);

        Map<String, Object> featureData = new HashMap<>();
        featureData.put("key", "val");

        myFeature.triggerSubmit(Collections.singletonMap("custom_feature_key", featureData));

        assertEquals(1, http.callCount);
        assertTrue(http.capturedJson.contains("identifier=12345678-1234-1234-1234-123456789abc"));
        assertTrue(http.capturedJson.contains("custom_feature_key={key=val}"));

        metrics.shutdown();
        assertTrue(myFeature.shutdown);
    }

    @Test
    public void testGetFeature() {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();
        MockTaskScheduler scheduler = new MockTaskScheduler();

        class AnotherFeature extends Feature {
        }
        AnotherFeature feature = new AnotherFeature();

        class FinderFeature extends Feature {
            void assertFoundFeatures(AnotherFeature expected) {
                assertTrue(getFeature(AnotherFeature.class).isPresent());
                assertSame(expected, getFeature(AnotherFeature.class).get());
                assertTrue(getFeature(Feature.class).isPresent());

                class UnregisteredFeature extends Feature {
                }
                assertFalse(getFeature(UnregisteredFeature.class).isPresent());
            }
        }
        FinderFeature finder = new FinderFeature();

        // Before builder builds and sets metrics, it should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> finder.getFeature(AnotherFeature.class));

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .scheduler(scheduler)
                .addFeature(feature)
                .addFeature(finder)
                .build();

        assertTrue(metrics.getFeature(AnotherFeature.class).isPresent());
        assertSame(feature, metrics.getFeature(AnotherFeature.class).get());
        assertTrue(metrics.getFeature(Feature.class).isPresent());

        class UnregisteredFeature extends Feature {
        }
        assertFalse(metrics.getFeature(UnregisteredFeature.class).isPresent());

        // Test from within FinderFeature
        finder.assertFoundFeatures(feature);
    }

    @Test
    public void testSubmitMetricsDisabled() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter http = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ((MockConfig) platform.getConfig()).submitMetrics = false;

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .serializer(serializer)
                .submitter(http)
                .build();

        metrics.submit("/v1/collect", Collections.emptyMap(), false);

        assertEquals(0, http.callCount);
    }
}
