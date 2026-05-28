package io.github.projectunified.faststats.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides version and name information for the FastStats SDK,
 * loaded from a Maven-filtered resource file at {@code /META-INF/faststats.properties}.
 */
public final class FastStatsVersion {
    private static final String NAME;
    private static final String VERSION;

    static {
        Properties properties = new Properties();
        try (InputStream stream = FastStatsVersion.class.getResourceAsStream("/META-INF/faststats.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
        }
        NAME = properties.getProperty("name", "unknown");
        VERSION = properties.getProperty("version", "unknown");
    }

    private FastStatsVersion() {
    }

    /**
     * Returns the SDK module name (e.g. {@code "faststats-core"}).
     *
     * @return the SDK name
     */
    public static String getName() {
        return NAME;
    }

    /**
     * Returns the SDK version (e.g. {@code "1.0-SNAPSHOT"}).
     *
     * @return the SDK version
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Returns the default User-Agent header value.
     *
     * @return the default user agent string
     */
    public static String getDefaultUserAgent() {
        return "FastStats Metrics/ProjectUnified/" + VERSION;
    }
}
