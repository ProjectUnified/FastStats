package io.github.projectunified.faststats.featureflag;

import io.github.projectunified.faststats.core.CapturingSubmitter;
import io.github.projectunified.faststats.core.Metrics;
import io.github.projectunified.faststats.core.MockPlatform;
import io.github.projectunified.faststats.core.SimpleSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class FeatureFlagManagerTest {

    @Test
    public void testDefineAndFetchBooleanFlag() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        FeatureFlagManager manager = new FeatureFlagManager();
        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(manager)
                .build();

        FeatureFlag<Boolean> flag = manager.define("test_boolean_flag", false);
        assertEquals("test_boolean_flag", flag.getId());
        assertEquals(false, flag.getDefaultValue());
        assertEquals(FeatureFlag.Type.BOOLEAN, flag.getType());
        assertTrue(flag.isExpired());
        assertFalse(flag.isValid());
        assertEquals(Optional.empty(), flag.getCached());

        // Configure mock response
        submitter.response = "{value=true}";

        CompletableFuture<Boolean> future = flag.fetch();
        assertTrue(future.get());
        assertTrue(flag.isValid());
        assertFalse(flag.isExpired());
        assertEquals(Optional.of(true), flag.getCached());
        assertEquals("https://flags.faststats.dev/v1/check", submitter.capturedPath);
        assertTrue(submitter.capturedJson.contains("key=test_boolean_flag"));
    }

    @Test
    public void testFetchStringFlagWithAttributes() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        FeatureFlagManager manager = new FeatureFlagManager();
        manager.attributes(map -> map.put("global_attr", "global_val"));

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(manager)
                .build();

        Map<String, Object> flagAttrs = new HashMap<>();
        flagAttrs.put("local_attr", "local_val");
        FeatureFlag<String> flag = manager.define("test_string_flag", "default_str", flagAttrs);

        submitter.response = "{value=fetched_str}";
        String val = flag.fetch().get();
        assertEquals("fetched_str", val);
        assertEquals(Optional.of("fetched_str"), flag.getCached());

        // Verify request payload contains both global and local attributes
        assertTrue(submitter.capturedJson.contains("global_attr=global_val"));
        assertTrue(submitter.capturedJson.contains("local_attr=local_val"));
    }

    @Test
    public void testOptInAndOptOut() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        FeatureFlagManager manager = new FeatureFlagManager();
        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(manager)
                .build();

        FeatureFlag<Number> flag = manager.define("test_num_flag", 10);

        // Opt in checks
        submitter.response = "{value=20}";
        Number val = flag.optIn().get();
        assertEquals(20.0, val.doubleValue());
        assertEquals("https://flags.faststats.dev/v1/check", submitter.capturedPath); // opt-in performs check after POST
        assertTrue(submitter.capturedJson.contains("key=test_num_flag"));

        // Opt out checks
        submitter.response = "{value=5}";
        val = flag.optOut().get();
        assertEquals(5.0, val.doubleValue());
    }

    @Test
    public void testTTLAndCaching() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        FeatureFlagManager manager = new FeatureFlagManager().ttl(Duration.ofMillis(50));
        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(manager)
                .build();

        FeatureFlag<Boolean> flag = manager.define("cache_flag", false);
        submitter.response = "{value=true}";

        // First fetch
        assertTrue(flag.whenReady().get());
        assertEquals(1, submitter.callCount);

        // Second fetch within TTL (should hit cache, no network call)
        assertTrue(flag.whenReady().get());
        assertEquals(1, submitter.callCount);

        // Sleep to exceed TTL
        Thread.sleep(100);

        // Third fetch (expired, should trigger network call)
        assertTrue(flag.isExpired());
        assertTrue(flag.whenReady().get());
        assertEquals(2, submitter.callCount);
    }
}
