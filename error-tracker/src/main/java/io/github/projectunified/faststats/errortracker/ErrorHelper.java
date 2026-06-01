package io.github.projectunified.faststats.errortracker;

import java.util.*;
import java.util.regex.Pattern;

final class ErrorHelper {
    private static final int MESSAGE_LENGTH = Math.min(1000, Integer.getInteger("faststats.message-length", 500));
    private static final int STACK_TRACE_LENGTH = Math.min(500, Integer.getInteger("faststats.stack-trace-length", 300));
    private static final int STACK_TRACE_LIMIT = Math.min(50, Integer.getInteger("faststats.stack-trace-limit", 15));
    private static final Set<String> allowedNames = Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList("minecraft", "server", "root", "ubuntu"))
    );

    public static Map<String, Object> compile(final TrackedError trackedError, final List<String> suppress,
                                              final List<Map.Entry<Pattern, String>> customPatterns,
                                              final Map<String, Object> defaultAttributes) {
        final Throwable error = trackedError.error();
        final Map<String, Object> report = new LinkedHashMap<>();
        final String message = getAnonymizedMessage(error, customPatterns);

        final List<String> stacktrace = new ArrayList<>();
        final String header = message != null
                ? error.getClass().getName() + ": " + message
                : error.getClass().getName();
        stacktrace.add(header);

        final StackTraceElement[] elements = error.getStackTrace();
        final List<String> stack = collapseStackTrace(elements);
        final List<String> list = new ArrayList<>(stack);
        if (suppress != null) {
            list.removeAll(suppress);
        }
        final int traces = Math.min(list.size(), STACK_TRACE_LIMIT);

        populateTraces(traces, list, elements, stacktrace);
        appendCauseChain(error.getCause(), stack, suppress, stacktrace, customPatterns);

        report.put("error", error.getClass().getName());
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

    private static void appendCauseChain(Throwable cause, final List<String> parentStack,
                                         final List<String> suppress, final List<String> stacktrace,
                                         final List<Map.Entry<Pattern, String>> customPatterns) {
        final List<String> toSuppress = new ArrayList<>(parentStack);
        if (suppress != null) {
            toSuppress.addAll(suppress);
        }
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cause != null && visited.add(cause)) {
            final String causeMessage = getAnonymizedMessage(cause, customPatterns);
            final String header = causeMessage != null
                    ? "Caused by: " + cause.getClass().getName() + ": " + causeMessage
                    : "Caused by: " + cause.getClass().getName();
            stacktrace.add(header);

            final StackTraceElement[] causeElements = cause.getStackTrace();
            final List<String> causeStack = collapseStackTrace(causeElements);
            final List<String> causeList = new ArrayList<>(causeStack);
            causeList.removeAll(toSuppress);
            final int causeTraces = Math.min(causeList.size(), STACK_TRACE_LIMIT);
            populateTraces(causeTraces, causeList, causeElements, stacktrace);

            cause = cause.getCause();
        }
    }

    private static void populateTraces(final int traces, final List<String> list, final StackTraceElement[] elements,
                                       final List<String> stacktrace) {
        for (int i = 0; i < traces; i++) {
            final String string = list.get(i);
            if (string.length() <= STACK_TRACE_LENGTH) {
                stacktrace.add("  at " + string);
            } else {
                stacktrace.add("  at " + string.substring(0, STACK_TRACE_LENGTH) + "...");
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

    private static String getAnonymizedMessage(final Throwable error, final List<Map.Entry<Pattern, String>> customPatterns) {
        final String message = error.getMessage();
        if (message == null) {
            return null;
        }
        String truncated = message.length() > MESSAGE_LENGTH
                ? message.substring(0, MESSAGE_LENGTH) + "..."
                : message;
        for (final Map.Entry<Pattern, String> entry : customPatterns) {
            truncated = entry.getKey().matcher(truncated).replaceAll(entry.getValue());
        }
        return truncated;
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
