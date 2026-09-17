package io.github.projectunified.faststats.errortracker;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An error report with tracking metadata.
 * <p>
 * The error is snapshotted when this report is created, so modifications of the
 * original {@link Throwable} afterwards do not change the report or its identity.
 * Reports are deduplicated by their error snapshot only; the metadata added later
 * ({@link #handled(boolean)}, {@link #attributes(Map)}) is not part of the identity.
 */
public final class TrackedError {
    private final ThrowableSnapshot error;
    private boolean handled = true;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * Creates a new tracked error.
     *
     * @param error the error
     */
    public TrackedError(Throwable error) {
        this.error = snapshot(error, null);
    }

    private static ThrowableSnapshot snapshot(Throwable error, Set<Throwable> visited) {
        String message = error.getMessage();
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (error.getCause() != null && visited == null) {
            visited = Collections.newSetFromMap(new IdentityHashMap<>());
        }
        if (visited != null && !visited.add(error)) {
            return null;
        }
        ThrowableSnapshot cause = error.getCause() != null ? snapshot(error.getCause(), visited) : null;
        StackTraceElement[] trace = stackTrace.length == 0 ? new Throwable().getStackTrace() : stackTrace;
        return new ErrorSnapshot(error.getClass(), message, cause, trace);
    }

    /**
     * Returns the snapshot of the tracked error.
     *
     * @return the error snapshot
     */
    public ThrowableSnapshot error() {
        return error;
    }

    /**
     * Returns whether the error was handled.
     *
     * @return whether the error was handled
     */
    public boolean handled() {
        return handled;
    }

    /**
     * Sets whether the error was handled.
     *
     * @param handled whether the error was handled
     * @return this tracked error
     */
    public TrackedError handled(boolean handled) {
        this.handled = handled;
        return this;
    }

    /**
     * Returns a copy of the additional error attributes.
     *
     * @return a copy of the additional error attributes
     */
    public Map<String, Object> attributes() {
        return new LinkedHashMap<>(attributes);
    }

    /**
     * Sets the additional error attributes.
     *
     * @param attributes the additional error attributes
     * @return this tracked error
     */
    public TrackedError attributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        return this;
    }

    /**
     * Modifies the additional error attributes using a consumer.
     *
     * @param consumer the consumer to modify attributes
     * @return this tracked error
     */
    public TrackedError attributes(Consumer<Map<String, Object>> consumer) {
        if (consumer != null) {
            consumer.accept(this.attributes);
        }
        return this;
    }

    /**
     * Sets the additional error attributes using a supplier.
     *
     * @param supplier the supplier of attributes
     * @return this tracked error
     */
    public TrackedError attributes(Supplier<Map<String, Object>> supplier) {
        if (supplier != null) {
            Map<String, Object> supplied = supplier.get();
            this.attributes = supplied == null ? new LinkedHashMap<>() : new LinkedHashMap<>(supplied);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TrackedError that = (TrackedError) o;
        return Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(error);
    }

    /**
     * An immutable snapshot of a throwable.
     */
    public interface ThrowableSnapshot {
        /**
         * Gets the type of the snapshotted throwable.
         *
         * @return the throwable type
         */
        Class<?> getType();

        /**
         * Gets the message of the snapshotted throwable.
         *
         * @return the throwable message
         */
        String getMessage();

        /**
         * Gets the snapshot of the cause, if any.
         *
         * @return the cause snapshot, or null
         */
        ThrowableSnapshot getCause();

        /**
         * Gets a copy of the stack trace of the snapshotted throwable.
         *
         * @return the stack trace
         */
        StackTraceElement[] getStackTrace();
    }

    private static final class ErrorSnapshot implements ThrowableSnapshot {
        private final Class<?> type;
        private final String message;
        private final ThrowableSnapshot cause;
        private final StackTraceElement[] stackTrace;

        private ErrorSnapshot(Class<?> type, String message, ThrowableSnapshot cause, StackTraceElement[] stackTrace) {
            this.type = type;
            this.message = message;
            this.cause = cause;
            this.stackTrace = stackTrace;
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public ThrowableSnapshot getCause() {
            return cause;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return stackTrace.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ErrorSnapshot that = (ErrorSnapshot) o;
            return Objects.equals(type, that.type)
                    && Objects.equals(message, that.message)
                    && Objects.equals(cause, that.cause)
                    && Arrays.equals(stackTrace, that.stackTrace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, message, cause, Arrays.hashCode(stackTrace));
        }
    }
}
