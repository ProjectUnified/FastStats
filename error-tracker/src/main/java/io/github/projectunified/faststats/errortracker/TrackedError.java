package io.github.projectunified.faststats.errortracker;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An error report with tracking metadata.
 */
public final class TrackedError {
    private final Throwable error;
    private boolean handled = true;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * Creates a new tracked error.
     *
     * @param error the error
     */
    public TrackedError(Throwable error) {
        this.error = error;
    }

    private static boolean deepEquals(Throwable first, Throwable second, Set<Throwable> visited) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first.getClass() != second.getClass()) return false;
        if (!Objects.equals(first.getMessage(), second.getMessage())) return false;
        if (!Arrays.equals(first.getStackTrace(), second.getStackTrace())) return false;
        if (!visited.add(first)) return true;
        return deepEquals(first.getCause(), second.getCause(), visited);
    }

    private static int hash(Throwable error, Set<Throwable> visited) {
        if (error == null || !visited.add(error)) return 0;
        return Objects.hash(
                error.getClass(),
                error.getMessage(),
                Arrays.hashCode(error.getStackTrace()),
                hash(error.getCause(), visited)
        );
    }

    /**
     * Returns the tracked error.
     *
     * @return the tracked error
     */
    public Throwable error() {
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackedError that = (TrackedError) o;
        return handled == that.handled
                && Objects.equals(attributes, that.attributes)
                && deepEquals(error, that.error, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes, handled, hash(error, Collections.newSetFromMap(new IdentityHashMap<>())));
    }
}
