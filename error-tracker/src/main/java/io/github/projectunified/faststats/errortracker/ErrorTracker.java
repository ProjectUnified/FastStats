package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Feature;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * A feature that tracks uncaught and handled errors, anonymizes sensitive info,
 * and submits report payloads.
 */
public class ErrorTracker extends Feature {
    private final ClassLoader loader;

    private final Map<String, Integer> collected = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> reports = new ConcurrentHashMap<>();

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
    private ScheduledExecutorService executor;

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

    @Override
    public Map<String, String> getDefaultProperties() {
        return Collections.singletonMap("submitErrors", "true");
    }

    @Override
    public synchronized void onStart() {
        if (loader != null) {
            attachErrorContext(loader);
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "faststats-error-tracker");
            thread.setDaemon(true);
            return thread;
        });

        long initialDelayMs = Long.getLong("faststats.initial-delay", 30) * 1000;
        long periodMs = 30 * 60 * 1000;

        executor.scheduleAtFixedRate(() -> {
            try {
                submitErrors();
            } catch (Throwable t) {
                // Ignore
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void onShutdown() {
        detachErrorContext();
        if (executor != null) {
            executor.shutdown();
            executor = null;
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
     */
    public void trackError(final String message) {
        trackError(message, true);
    }

    /**
     * Tracks a handled error.
     *
     * @param error the error
     */
    public void trackError(final Throwable error) {
        trackError(error, true);
    }

    /**
     * Tracks an error with the given message.
     *
     * @param message the error message
     * @param handled whether the error was handled
     */
    public void trackError(final String message, final boolean handled) {
        trackError(new RuntimeException(message), handled);
    }

    /**
     * Tracks an error.
     *
     * @param error   the error
     * @param handled whether the error was handled
     */
    public void trackError(final Throwable error, final boolean handled) {
        try {
            if (isIgnored(error, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                return;
            }
            final Map<String, Object> compiled = ErrorHelper.compile(error, null, handled, anonymizationEntries);
            final String hashed = MurmurHash3.hash(compiled.toString());

            synchronized (this) {
                if (collected.compute(hashed, (k, v) -> v == null ? 1 : v + 1) > 1) {
                    return;
                }
                reports.put(hashed, compiled);
            }
        } catch (final NoClassDefFoundError ignored) {
        }
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

        final String buildId = getProperty("build-id", "unknown");

        final Map<String, Map<String, Object>> reportsSnapshot;
        final Map<String, Integer> collectedSnapshot;

        synchronized (this) {
            if (reports.isEmpty() && collected.isEmpty()) {
                return;
            }
            reportsSnapshot = new LinkedHashMap<>(reports);
            collectedSnapshot = new LinkedHashMap<>(collected);
            reports.clear();
            collected.replaceAll((k, v) -> 0);
        }

        Map<String, Object> errorsMap = new LinkedHashMap<>();

        reportsSnapshot.forEach((hash, report) -> {
            Map<String, Object> copy = new LinkedHashMap<>(report);
            copy.put("hash", hash);
            copy.put("buildId", buildId);
            int count = collectedSnapshot.getOrDefault(hash, 1);
            if (count > 1) {
                copy.put("count", count);
            }
            errorsMap.put(hash, copy);
        });

        collectedSnapshot.forEach((hash, count) -> {
            if (count <= 0 || reportsSnapshot.containsKey(hash)) {
                return;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("hash", hash);
            if (count > 1) {
                entry.put("count", count);
            }
            errorsMap.put(hash, entry);
        });

        if (errorsMap.isEmpty()) {
            return;
        }

        submit(Collections.singletonMap("errors", errorsMap));
    }
}
