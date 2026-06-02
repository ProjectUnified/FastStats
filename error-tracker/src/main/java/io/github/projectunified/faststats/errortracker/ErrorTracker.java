package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.BuildInfo;
import io.github.projectunified.faststats.core.Feature;
import io.github.projectunified.faststats.core.TaskScheduler;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A feature that tracks uncaught and handled errors, anonymizes sensitive info,
 * and submits report payloads.
 */
public class ErrorTracker extends Feature {
    private final ClassLoader loader;

    private final Map<TrackedError, Integer> reports = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    private final Map<Class<? extends Throwable>, Set<Pattern>> ignoredTypedPatterns = new ConcurrentHashMap<>();
    private final Set<Class<? extends Throwable>> ignoredTypes = new CopyOnWriteArraySet<>();
    private final Set<Pattern> ignoredPatterns = new CopyOnWriteArraySet<>();
    private final List<Map.Entry<Pattern, String>> anonymizationEntries = new CopyOnWriteArrayList<Map.Entry<Pattern, String>>() {
        {
            add(new AbstractMap.SimpleEntry<>(ErrorHelper.ipv4Pattern(), "[IP hidden]"));
            add(new AbstractMap.SimpleEntry<>(ErrorHelper.ipv6Pattern(), "[IP hidden]"));
            add(new AbstractMap.SimpleEntry<>(ErrorHelper.userHomePathPattern(), "$1$2$3[username hidden]"));
            add(new AbstractMap.SimpleEntry<>(ErrorHelper.discordWebhookPattern(), "$1[token hidden]"));
            add(new AbstractMap.SimpleEntry<>(ErrorHelper.jdbcUrlPattern(), "$1[password hidden]$2"));
        }
    };

    private volatile BiConsumer<ClassLoader, Throwable> errorEvent = null;
    private volatile UncaughtExceptionHandler originalHandler = null;
    private volatile boolean attached = false;
    private TaskScheduler.Task task;

    /**
     * Creates a new error tracker feature that is context-unaware.
     */
    public ErrorTracker() {
        this(null);
    }

    /**
     * Creates a new error tracker feature with the specified class loader.
     *
     * @param loader the class loader context
     */
    public ErrorTracker(ClassLoader loader) {
        this.loader = loader;
        ErrorHelper.usernamePattern().ifPresent(pattern ->
                anonymizationEntries.add(new AbstractMap.SimpleEntry<>(pattern, "[username hidden]"))
        );
    }

    /**
     * Creates a context-aware error tracker feature.
     *
     * @return a new error tracker feature
     */
    public static ErrorTracker contextAware() {
        return new ErrorTracker(ErrorTracker.class.getClassLoader());
    }

    /**
     * Creates a context-unaware error tracker feature.
     *
     * @return a new error tracker feature
     */
    public static ErrorTracker contextUnaware() {
        return new ErrorTracker(null);
    }

    /**
     * Returns the global error context attributes configured for this tracker.
     *
     * @return the global error context attributes
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Modifies the global error context attributes using a consumer.
     *
     * @param consumer the consumer to modify attributes
     * @return this instance
     */
    public ErrorTracker attributes(Consumer<Map<String, Object>> consumer) {
        if (consumer != null) {
            consumer.accept(this.attributes);
        }
        return this;
    }

    /**
     * Sets the global error context attributes using a supplier.
     *
     * @param supplier the supplier of attributes
     * @return this instance
     */
    public ErrorTracker attributes(Supplier<Map<String, Object>> supplier) {
        if (supplier != null) {
            Map<String, Object> supplied = supplier.get();
            this.attributes.clear();
            if (supplied != null) {
                this.attributes.putAll(supplied);
            }
        }
        return this;
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return Collections.singletonMap("submitErrors", "true");
    }

    @Override
    public synchronized void onStart() {
        if (loader != null) {
            attachErrorContext(loader);
        }

        long initialDelayMs = Long.getLong("faststats.initial-delay", 30) * 1000;
        long periodMs = 30 * 60 * 1000;

        task = getScheduler().schedule(() -> {
            try {
                submitErrors();
            } catch (Throwable t) {
                // Ignore
            }
        }, initialDelayMs, periodMs);
    }

    @Override
    public synchronized void onShutdown() {
        detachErrorContext();
        if (task != null) {
            task.cancel();
            task = null;
        }
        try {
            submitErrors();
        } catch (Throwable t) {
            // Ignore
        }
    }

    /**
     * Tracks a handled error with the given message.
     *
     * @param message the error message
     * @return the tracked error
     */
    public TrackedError trackError(final String message) {
        return trackError(message, true);
    }

    /**
     * Tracks a handled error.
     *
     * @param error the error
     * @return the tracked error
     */
    public TrackedError trackError(final Throwable error) {
        return trackError(error, true);
    }

    /**
     * Tracks an error with the given message.
     *
     * @param message the error message
     * @param handled whether the error was handled
     * @return the tracked error
     */
    public TrackedError trackError(final String message, final boolean handled) {
        return trackError(new RuntimeException(message), handled);
    }

    /**
     * Tracks an error.
     *
     * @param error   the error
     * @param handled whether the error was handled
     * @return the tracked error
     */
    public TrackedError trackError(final Throwable error, final boolean handled) {
        final TrackedError trackedError = new TrackedError(error).handled(handled);
        try {
            if (isIgnored(error, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                return trackedError;
            }

            reports.compute(trackedError, (k, v) -> v == null ? 1 : v + 1);
        } catch (final NoClassDefFoundError ignored) {
        }
        return trackedError;
    }

    private boolean isIgnored(final Throwable error, final Set<Throwable> visited) {
        if (error == null || !visited.add(error)) {
            return false;
        }

        if (ignoredTypes.contains(error.getClass())) {
            return true;
        }

        final String message = error.getMessage() != null ? error.getMessage() : "";
        for (Pattern pattern : ignoredPatterns) {
            if (pattern.matcher(message).find()) {
                return true;
            }
        }

        final Set<Pattern> patterns = ignoredTypedPatterns.get(error.getClass());
        if (patterns != null) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(message).find()) {
                    return true;
                }
            }
        }

        return isIgnored(error.getCause(), visited);
    }

    /**
     * Adds an error type that will not be reported.
     *
     * @param type the error type
     * @return this instance
     */
    public synchronized ErrorTracker ignoreError(final Class<? extends Throwable> type) {
        ignoredTypes.add(type);
        return this;
    }

    /**
     * Adds a pattern that will be matched against error messages to ignore them.
     *
     * @param pattern the pattern
     * @return this instance
     */
    public synchronized ErrorTracker ignoreError(final Pattern pattern) {
        ignoredPatterns.add(pattern);
        return this;
    }

    /**
     * Adds a pattern string that will be matched against error messages to ignore them.
     *
     * @param pattern the regex pattern
     * @return this instance
     */
    public ErrorTracker ignoreError(final String pattern) {
        return ignoreError(Pattern.compile(pattern));
    }

    /**
     * Adds an error type and message pattern that will be ignored.
     *
     * @param type    the error type
     * @param pattern the pattern
     * @return this instance
     */
    public synchronized ErrorTracker ignoreError(final Class<? extends Throwable> type, final Pattern pattern) {
        ignoredTypedPatterns.computeIfAbsent(type, k -> new CopyOnWriteArraySet<>()).add(pattern);
        return this;
    }

    /**
     * Adds an error type and message pattern string that will be ignored.
     *
     * @param type    the error type
     * @param pattern the regex pattern
     * @return this instance
     */
    public ErrorTracker ignoreError(final Class<? extends Throwable> type, final String pattern) {
        return ignoreError(type, Pattern.compile(pattern));
    }

    /**
     * Adds an anonymization pattern replacement rule.
     *
     * @param pattern     the pattern to match
     * @param replacement the replacement string
     * @return this instance
     */
    public synchronized ErrorTracker anonymize(final Pattern pattern, final String replacement) {
        anonymizationEntries.add(new AbstractMap.SimpleEntry<>(pattern, replacement));
        return this;
    }

    /**
     * Adds an anonymization pattern replacement rule.
     *
     * @param pattern     the regex pattern to match
     * @param replacement the replacement string
     * @return this instance
     */
    public ErrorTracker anonymize(final String pattern, final String replacement) {
        return anonymize(Pattern.compile(pattern), replacement);
    }

    /**
     * Attaches uncaught error handling using the specified class loader.
     *
     * @param classLoader the class loader
     * @throws IllegalStateException if already attached
     */
    public synchronized void attachErrorContext(final ClassLoader classLoader) throws IllegalStateException {
        if (attached) {
            throw new IllegalStateException("Error context already attached");
        }
        originalHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            final UncaughtExceptionHandler handler = originalHandler;
            if (handler != null) {
                handler.uncaughtException(thread, error);
            }
            try {
                if (classLoader != null && !ErrorHelper.isSameLoader(classLoader, error)) {
                    return;
                }
                final BiConsumer<ClassLoader, Throwable> event = errorEvent;
                if (event != null) {
                    event.accept(classLoader, error);
                }
                trackError(error, false);
            } catch (final Throwable t) {
                trackError(t, false);
            }
        });
        attached = true;
    }

    /**
     * Detaches error context handling and restores the original handler.
     */
    public synchronized void detachErrorContext() {
        if (!attached) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(originalHandler);
        originalHandler = null;
        attached = false;
    }

    /**
     * Gets whether an error context is currently attached.
     *
     * @return true if attached, false otherwise
     */
    public synchronized boolean isContextAttached() {
        return attached;
    }

    /**
     * Gets the error event handler.
     *
     * @return the handler
     */
    public synchronized Optional<BiConsumer<ClassLoader, Throwable>> getContextErrorHandler() {
        return Optional.ofNullable(errorEvent);
    }

    /**
     * Sets the error event handler.
     *
     * @param errorEvent the handler
     */
    public synchronized void setContextErrorHandler(final BiConsumer<ClassLoader, Throwable> errorEvent) {
        this.errorEvent = errorEvent;
    }

    void submitErrors() throws Exception {
        if (!Boolean.parseBoolean(getProperty("submitErrors", "true"))) {
            return;
        }

        String serverPath = System.getProperty("faststats.error-tracker-server");
        if (serverPath == null) {
            serverPath = getProperty("error-tracker-server", "/v1/error");
        }

        final String buildId = getProperty("build-id", BuildInfo.getBuildId());

        final Map<TrackedError, Integer> reportsSnapshot;

        synchronized (this) {
            if (reports.isEmpty()) {
                return;
            }
            reportsSnapshot = new LinkedHashMap<>(reports);
            reports.clear();
        }

        List<Map<String, Object>> errorsList = new ArrayList<>();

        reportsSnapshot.forEach((trackedError, count) -> {
            Map<String, Object> compiled = ErrorHelper.compile(trackedError, null, anonymizationEntries, attributes);
            String hash = Integer.toHexString(trackedError.hashCode());
            compiled.put("hash", hash);
            compiled.put("buildId", buildId);
            if (count > 1) {
                compiled.put("count", count);
            }
            errorsList.add(compiled);
        });

        if (errorsList.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildId", buildId);
        payload.put("language", "java");
        payload.put("project_name", getProperty("project-name", getProperty("project_name", "unknown")));
        payload.put("sdk_name", BuildInfo.getName());
        payload.put("sdk_version", BuildInfo.getVersion());
        payload.put("errors", errorsList);
        payload.put("context", getDefaultContext());

        submit(serverPath, payload, true);
    }
}
