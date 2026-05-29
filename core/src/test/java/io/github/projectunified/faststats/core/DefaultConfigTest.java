package io.github.projectunified.faststats.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultConfigTest {

    @Test
    public void testFirstRunAndSave(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.properties");
        assertFalse(Files.exists(configFile));

        // First run
        DefaultConfig config = DefaultConfig.read(configFile);
        assertTrue(config.isFirstRun());
        assertNotNull(config.getServerId());
        assertFalse(config.isEnabled());
        assertTrue(config.isSubmitAdditionalMetrics());
        assertFalse(config.isDebug());

        // File should be saved automatically on first run
        assertTrue(Files.exists(configFile));

        // Second run, same file
        DefaultConfig secondConfig = DefaultConfig.read(configFile);
        assertFalse(secondConfig.isFirstRun());
        assertEquals(config.getServerId(), secondConfig.getServerId());
        assertFalse(secondConfig.isEnabled());
    }

    @Test
    public void testReadExistingConfig(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.properties");
        UUID expectedUuid = UUID.randomUUID();

        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            writer.write("serverId=" + expectedUuid.toString() + "\n");
            writer.write("enabled=false\n");
            writer.write("submitAdditionalMetrics=false\n");
            writer.write("debug=true\n");
        }

        DefaultConfig config = DefaultConfig.read(configFile);
        assertFalse(config.isFirstRun());
        assertEquals(expectedUuid, config.getServerId());
        assertFalse(config.isEnabled());
        assertFalse(config.isSubmitAdditionalMetrics());
        assertTrue(config.isDebug());
    }

    @Test
    public void testCustomProperties(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.properties");
        DefaultConfig config = DefaultConfig.read(configFile);

        Map<String, String> props1 = new HashMap<>();
        props1.put("my.custom.prop", "foo");
        config.setDefaultProperty(props1);
        assertEquals("foo", config.getProperty("my.custom.prop", "default"));

        // Overwrite check (should not change because it's already set)
        Map<String, String> props2 = new HashMap<>();
        props2.put("my.custom.prop", "bar");
        config.setDefaultProperty(props2);
        assertEquals("foo", config.getProperty("my.custom.prop", "default"));

        // Re-read file to verify persistence
        DefaultConfig reRead = DefaultConfig.read(configFile);
        assertEquals("foo", reRead.getProperty("my.custom.prop", "default"));
    }
}
