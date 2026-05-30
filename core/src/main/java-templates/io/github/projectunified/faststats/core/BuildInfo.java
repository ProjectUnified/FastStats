package io.github.projectunified.faststats.core;

/**
 * Provides build information for the FastStats SDK,
 * filtered and injected directly by Maven during compilation.
 */
public final class BuildInfo {
    private static final String NAME = "${project.name}";
    private static final String VERSION = "${project.version}";
    private static final String BUILD_ID = "${build-id}";

    private BuildInfo() {
    }

    /**
     * Returns the SDK module name.
     *
     * @return the SDK name
     */
    public static String getName() {
        return NAME;
    }

    /**
     * Returns the SDK version.
     *
     * @return the SDK version
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Returns the build identifier.
     *
     * @return the build identifier
     */
    public static String getBuildId() {
        return BUILD_ID;
    }

    /**
     * Returns the default User-Agent header value.
     *
     * @return the default user agent string
     */
    public static String getDefaultUserAgent() {
        return "ProjectUnified FastStats/" + VERSION;
    }
}
