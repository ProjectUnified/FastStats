package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Metrics;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrorTrackerTest {

    @Test
    public void testSubmitErrors_noErrors() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ErrorTracker tracker = ErrorTracker.contextUnaware();
        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(tracker)
                .build();

        // Trigger submit when empty - nothing should be submitted
        tracker.submitErrors();
        assertEquals(0, submitter.callCount);
    }

    @Test
    public void testTrackError_normalFlow() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ErrorTracker tracker = ErrorTracker.contextUnaware();
        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(tracker)
                .build();

        tracker.trackError(new RuntimeException("Test exception message"));
        tracker.submitErrors();

        assertEquals(1, submitter.callCount);
        assertTrue(submitter.capturedJson.contains("java.lang.RuntimeException"));
        assertTrue(submitter.capturedJson.contains("Test exception message"));
        assertTrue(submitter.capturedJson.contains("handled=true"));
    }

    @Test
    public void testTrackError_deduplication() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ErrorTracker tracker = ErrorTracker.contextUnaware();
        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(tracker)
                .build();

        tracker.trackError(new RuntimeException("Repeated error"));
        tracker.trackError(new RuntimeException("Repeated error"));
        tracker.submitErrors();

        assertEquals(1, submitter.callCount);
        assertTrue(submitter.capturedJson.contains("count=2"));
    }

    @Test
    public void testIgnoreError_byType() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ErrorTracker tracker = ErrorTracker.contextUnaware()
                .ignoreError(IllegalArgumentException.class);

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(tracker)
                .build();

        tracker.trackError(new IllegalArgumentException("Ignore me"));
        tracker.trackError(new RuntimeException("Keep me"));
        tracker.submitErrors();

        assertEquals(1, submitter.callCount);
        assertFalse(submitter.capturedJson.contains("IllegalArgumentException"));
        assertTrue(submitter.capturedJson.contains("java.lang.RuntimeException"));
    }

    @Test
    public void testIgnoreError_byPattern() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ErrorTracker tracker = ErrorTracker.contextUnaware()
                .ignoreError(Pattern.compile(".*secret.*"))
                .ignoreError(".*ignoredPattern.*");

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(tracker)
                .build();

        tracker.trackError(new RuntimeException("Contains secret database credentials"));
        tracker.trackError(new RuntimeException("Matches ignoredPattern string"));
        tracker.trackError(new RuntimeException("Safe exception"));
        tracker.submitErrors();

        assertEquals(1, submitter.callCount);
        assertFalse(submitter.capturedJson.contains("secret"));
        assertFalse(submitter.capturedJson.contains("ignoredPattern"));
        assertTrue(submitter.capturedJson.contains("Safe exception"));
    }

    @Test
    public void testIgnoreError_byTypeAndPattern() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ErrorTracker tracker = ErrorTracker.contextUnaware()
                .ignoreError(SocketException.class, Pattern.compile("Connection reset"))
                .ignoreError(IOException.class, "Broken pipe");

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(tracker)
                .build();

        tracker.trackError(new SocketException("Connection reset"));
        tracker.trackError(new IOException("Broken pipe"));
        tracker.trackError(new SocketException("Connection timed out"));
        tracker.submitErrors();

        assertEquals(1, submitter.callCount);
        assertFalse(submitter.capturedJson.contains("Connection reset"));
        assertFalse(submitter.capturedJson.contains("Broken pipe"));
        assertTrue(submitter.capturedJson.contains("Connection timed out"));
    }

    @Test
    public void testAnonymization() throws Exception {
        MockPlatform platform = new MockPlatform();
        CapturingSubmitter submitter = new CapturingSubmitter();
        SimpleSerializer serializer = new SimpleSerializer();

        ErrorTracker tracker = ErrorTracker.contextUnaware()
                .anonymize(Pattern.compile("API_KEY=[a-zA-Z0-9]+"), "API_KEY=[redacted]")
                .anonymize("token=[a-zA-Z0-9]+", "token=[redacted]");

        Metrics metrics = Metrics.builder()
                .platform(platform)
                .submitter(submitter)
                .serializer(serializer)
                .addFeature(tracker)
                .build();

        // Default redactions
        tracker.trackError(new RuntimeException("IP address is 192.168.1.50"));
        tracker.trackError(new RuntimeException("Webhook: https://discord.com/api/webhooks/12345/abc-xyz"));
        // Custom redactions
        tracker.trackError(new RuntimeException("Failed request API_KEY=abcdef123 token=secret"));
        tracker.submitErrors();

        assertEquals(1, submitter.callCount);
        // Assert IP is hidden
        assertTrue(submitter.capturedJson.contains("[IP hidden]"));
        assertFalse(submitter.capturedJson.contains("192.168.1.50"));
        // Assert webhook token is hidden
        assertTrue(submitter.capturedJson.contains("[token hidden]"));
        assertFalse(submitter.capturedJson.contains("abc-xyz"));
        // Assert custom redactions
        assertTrue(submitter.capturedJson.contains("API_KEY=[redacted]"));
        assertTrue(submitter.capturedJson.contains("token=[redacted]"));
        assertFalse(submitter.capturedJson.contains("abcdef123"));
        assertFalse(submitter.capturedJson.contains("secret"));
    }

    @Test
    public void testContextAttachment() {
        ErrorTracker tracker = ErrorTracker.contextAware();
        assertFalse(tracker.isContextAttached());

        tracker.onStart();
        assertTrue(tracker.isContextAttached());

        tracker.onShutdown();
        assertFalse(tracker.isContextAttached());
    }
}
