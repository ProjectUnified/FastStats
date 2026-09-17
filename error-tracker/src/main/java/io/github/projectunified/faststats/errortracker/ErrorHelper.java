package io.github.projectunified.faststats.errortracker;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Compiles tracked errors into submission payloads, including anonymization,
 * truncation and stack trace collapsing.
 */
final class ErrorHelper {
    private static final int MAX_MESSAGE_LENGTH = limit("faststats.message-length", 1000, 4000);
    private static final int MAX_FRAME_SIZE = limit("faststats.stack-trace-length", 300, 1200);
    private static final int MAX_STACK_SIZE = limit("faststats.stack-trace-limit", 30, 100);

    private static final Set<String> allowedNames = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("minecraft", "server", "root", "ubuntu"))
    );
    private static final List<Map.Entry<Pattern, String>> DEFAULT_ANONYMIZATION_ENTRIES = defaultAnonymizationEntries();

    private static int limit(final String propertyName, final int defaultValue, final int maximum) {
        return Math.max(1, Math.min(Integer.getInteger(propertyName, defaultValue), maximum));
    }

    public static List<Map.Entry<Pattern, String>> defaultAnonymizationEntries() {
        List<Map.Entry<Pattern, String>> entries = new ArrayList<>();
        entries.add(new AbstractMap.SimpleEntry<>(ipv4Pattern(), "[IP hidden]"));
        entries.add(new AbstractMap.SimpleEntry<>(ipv6Pattern(), "[IP hidden]"));
        entries.add(new AbstractMap.SimpleEntry<>(userHomePathPattern(), "$1$2$3[username hidden]"));
        entries.add(new AbstractMap.SimpleEntry<>(discordWebhookPattern(), "$1[token hidden]"));
        entries.add(new AbstractMap.SimpleEntry<>(jdbcUrlPattern(), "$1[password hidden]$2"));
        usernamePattern().ifPresent(pattern ->
                entries.add(new AbstractMap.SimpleEntry<>(pattern, "[username hidden]"))
        );
        return Collections.unmodifiableList(entries);
    }

    public static Map<String, Object> compile(final TrackedError trackedError, final List<String> suppress,
                                              final List<Map.Entry<Pattern, String>> customPatterns,
                                              final Map<String, Object> defaultAttributes) {
        final TrackedError.ThrowableSnapshot error = trackedError.error();
        final List<Map.Entry<Pattern, String>> patterns = new ArrayList<>(customPatterns);
        patterns.addAll(DEFAULT_ANONYMIZATION_ENTRIES);

        final Map<String, Object> report = new LinkedHashMap<>();

        final List<String> stacktrace = new ArrayList<>();
        final String rootMessage = getAnonymizedMessage(error.getMessage(), patterns);
        final String header = rootMessage != null
                ? error.getType().getName() + ": " + rootMessage
                : error.getType().getName();
        stacktrace.add(header);

        final StackTraceElement[] elements = error.getStackTrace();
        final List<String> stack = collapseStackTrace(elements);
        final List<String> list = new ArrayList<>(stack);
        if (suppress != null) {
            list.removeAll(suppress);
        }
        final int traces = Math.min(list.size(), MAX_STACK_SIZE);

        populateTraces(traces, list, elements, stacktrace);
        appendCauseChain(error.getCause(), stack, suppress, stacktrace, patterns);

        report.put("error", error.getType().getName());
        final String message = getAnonymizedMessage(findFirstMessage(error), patterns);
        if (message != null) {
            report.put("message", message);
        }

        report.put("stack", stacktrace);
        report.put("handled", trackedError.handled());

        final Map<String, Object> context = new LinkedHashMap<>();
        if (defaultAttributes != null) {
            context.putAll(defaultAttributes);
        }
        context.putAll(trackedError.attributes());
        if (!context.isEmpty()) {
            report.put("context", context);
        }

        return report;
    }

    private static String findFirstMessage(final TrackedError.ThrowableSnapshot error) {
        TrackedError.ThrowableSnapshot current = error;
        while (current != null) {
            if (current.getMessage() != null) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }

    private static void appendCauseChain(TrackedError.ThrowableSnapshot cause, final List<String> parentStack,
                                         final List<String> suppress, final List<String> stacktrace,
                                         final List<Map.Entry<Pattern, String>> customPatterns) {
        final List<String> toSuppress = new ArrayList<>(parentStack);
        if (suppress != null) {
            toSuppress.addAll(suppress);
        }
        while (cause != null) {
            final String causeMessage = getAnonymizedMessage(cause.getMessage(), customPatterns);
            final String header = causeMessage != null
                    ? "Caused by: " + cause.getType().getName() + ": " + causeMessage
                    : "Caused by: " + cause.getType().getName();
            stacktrace.add(header);

            final StackTraceElement[] causeElements = cause.getStackTrace();
            final List<String> causeStack = collapseStackTrace(causeElements);
            final List<String> causeList = new ArrayList<>(causeStack);
            causeList.removeAll(toSuppress);
            final int causeTraces = Math.min(causeList.size(), MAX_STACK_SIZE);
            populateTraces(causeTraces, causeList, causeElements, stacktrace);

            cause = cause.getCause();
        }
    }

    private static void populateTraces(final int traces, final List<String> list, final StackTraceElement[] elements,
                                       final List<String> stacktrace) {
        for (int i = 0; i < traces; i++) {
            final String string = list.get(i);
            if (MAX_FRAME_SIZE < 0 || string.length() <= MAX_FRAME_SIZE) {
                stacktrace.add("  at " + string);
            } else {
                stacktrace.add("  at " + string.substring(0, MAX_FRAME_SIZE) + "...");
            }
        }
        if (traces > 0 && traces < list.size()) {
            stacktrace.add("  ... " + (list.size() - traces) + " more");
        } else {
            final int i = elements.length - list.size();
            if (i > 0) {
                stacktrace.add("  ... " + i + " more");
            }
        }
    }

    private static List<String> collapseStackTrace(final StackTraceElement[] trace) {
        final List<String> lines = new ArrayList<>();
        for (StackTraceElement element : trace) {
            lines.add(element.toString());
        }
        return collapseRepeatingPattern(lines);
    }

    private static List<String> collapseRepeatingPattern(final List<String> lines) {
        final List<String> deduplicated = collapseConsecutiveDuplicates(lines);

        final int n = deduplicated.size();

        for (int cycleLen = 1; cycleLen <= n / 2; cycleLen++) {
            boolean isPattern = true;
            int repetitions = 0;

            for (int i = 0; i < n; i++) {
                if (!deduplicated.get(i).equals(deduplicated.get(i % cycleLen))) {
                    isPattern = false;
                    break;
                }
                if (i > 0 && i % cycleLen == 0) {
                    repetitions++;
                }
            }

            if (isPattern && repetitions >= 2) {
                return deduplicated.subList(0, cycleLen);
            }
        }

        return deduplicated;
    }

    private static List<String> collapseConsecutiveDuplicates(final List<String> lines) {
        if (lines.isEmpty()) {
            return lines;
        }

        final List<String> result = new ArrayList<>();
        String previous = null;

        for (final String line : lines) {
            if (line.equals(previous)) {
                continue;
            }
            result.add(line);
            previous = line;
        }

        return result;
    }

    public static boolean isSameLoader(final ClassLoader loader, final Throwable error) {
        return isSameLoader(loader, error, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean isSameLoader(final ClassLoader loader, final Throwable error, final Set<Throwable> visited) {
        if (error == null || !visited.add(error)) {
            return false;
        }

        final StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) {
            return isSameLoader(loader, error.getCause(), visited);
        }

        final int firstNonLibraryIndex = findFirstNonLibraryFrameIndex(stackTrace);
        if (firstNonLibraryIndex == -1) {
            return isSameLoader(loader, error.getCause(), visited);
        }

        final int framesToCheck = Math.min(5, stackTrace.length - firstNonLibraryIndex);

        for (int i = 0; i < framesToCheck; i++) {
            final StackTraceElement frame = stackTrace[firstNonLibraryIndex + i];
            if (isLibraryClass(frame.getClassName())) {
                continue;
            }
            if (!isFromLoader(frame, loader)) {
                return isSameLoader(loader, error.getCause(), visited);
            }
        }

        return true;
    }

    private static int findFirstNonLibraryFrameIndex(final StackTraceElement[] stackTrace) {
        for (int i = 0; i < stackTrace.length; i++) {
            if (!isLibraryClass(stackTrace[i].getClassName())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLibraryClass(final String className) {
        return className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.")
                || className.startsWith("jdk.");
    }

    private static boolean isFromLoader(final StackTraceElement frame, final ClassLoader loader) {
        try {
            final Class<?> clazz = Class.forName(frame.getClassName(), false, loader);
            return isSameClassLoader(clazz.getClassLoader(), loader);
        } catch (final Throwable t) {
            return false;
        }
    }

    private static boolean isSameClassLoader(final ClassLoader classLoader, final ClassLoader loader) {
        if (classLoader == loader) {
            return true;
        }
        ClassLoader current = classLoader;
        while (current != null && current != loader) {
            current = current.getParent();
        }
        return loader == current;
    }

    private static String getAnonymizedMessage(final String message, final List<Map.Entry<Pattern, String>> patterns) {
        if (message == null) {
            return null;
        }
        final String truncated = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) + "..."
                : message;
        return anonymize(truncated, patterns);
    }

    private static String anonymize(final String message, final List<Map.Entry<Pattern, String>> patterns) {
        String anonymized = message;
        for (final Map.Entry<Pattern, String> entry : patterns) {
            anonymized = entry.getKey().matcher(anonymized).replaceAll(entry.getValue());
        }
        return anonymized;
    }

    public static Pattern discordWebhookPattern() {
        return Pattern.compile("(https://discord\\.com/api/webhooks/\\d+/)[\\w-]+");
    }

    public static Pattern ipv4Pattern() {
        return Pattern.compile("\\b(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b");
    }

    public static Pattern ipv6Pattern() {
        return Pattern.compile("(?i)\\b([0-9a-f]{1,4}:){7}[0-9a-f]{1,4}\\b|" + // Full form
                "(?i)\\b([0-9a-f]{1,4}:){1,7}:\\b|" +                          // Trailing ::
                "(?i)\\b([0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}\\b|" +             // :: in middle (1 group after)
                "(?i)\\b([0-9a-f]{1,4}:){1,5}(:[0-9a-f]{1,4}){1,2}\\b|" +      // :: in middle (2 groups after)
                "(?i)\\b([0-9a-f]{1,4}:){1,4}(:[0-9a-f]{1,4}){1,3}\\b|" +      // :: in middle (3 groups after)
                "(?i)\\b([0-9a-f]{1,4}:){1,3}(:[0-9a-f]{1,4}){1,4}\\b|" +      // :: in middle (4 groups after)
                "(?i)\\b([0-9a-f]{1,4}:){1,2}(:[0-9a-f]{1,4}){1,5}\\b|" +      // :: in middle (5 groups after)
                "(?i)\\b[0-9a-f]{1,4}:(:[0-9a-f]{1,4}){1,6}\\b|" +             // :: in middle (6 groups after)
                "(?i)\\b:(:[0-9a-f]{1,4}){1,7}\\b|" +                          // Leading ::
                "(?i)\\b::([0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4}\\b|" +            // :: at start
                "(?i)\\b::\\b");                                               // Just ::
    }

    public static Pattern jdbcUrlPattern() {
        return Pattern.compile("(jdbc:[^:]+://[^:]+:(?:\\d+:)?)[^@]+(@)");
    }

    public static Pattern userHomePathPattern() {
        return Pattern.compile("(/home/)[^/\\s]+" +       // Linux: /home/username
                "|(/Users/)[^/\\s]+" +                    // macOS: /Users/username
                "|((?i)[A-Z]:\\\\Users\\\\)[^\\\\\\s]+"); // Windows: A-Z:\\Users\\username
    }

    public static Optional<Pattern> usernamePattern() {
        return Optional.ofNullable(System.getProperty("user.name"))
                .filter(s -> s.trim().length() > 2)
                .filter(s -> !allowedNames.contains(s.toLowerCase(Locale.ROOT)))
                .map(Pattern::quote)
                .map(s -> Pattern.compile(s, Pattern.CASE_INSENSITIVE));
    }
}
