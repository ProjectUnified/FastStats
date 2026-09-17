package io.github.projectunified.faststats.core;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Default {@link Metric} implementation that validates the metric name.
 *
 * @param <T> the type of value this metric holds
 */
final class SimpleMetric<T> implements Metric<T> {
    static final String NAME_PATTERN = "^[a-z0-9_]+$";

    private final String name;
    private final Callable<T> callable;

    SimpleMetric(String name, Callable<T> callable) {
        if (name == null || !name.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException("Invalid metric name '" + name + "', must match '" + NAME_PATTERN + "'");
        }
        this.name = name;
        this.callable = callable;
    }

    /**
     * Normalizes a metric value to the types supported by the telemetry payload.
     * <p>
     * Maps, collections and arrays are normalized recursively, so nested
     * structures are preserved by the serializer instead of being flattened
     * into their {@link Object#toString()} representation.
     *
     * @param value the value to normalize
     * @return the normalized value
     */
    static Object normalize(Object value) {
        if (value == null
                || value instanceof Boolean
                || value instanceof Number
                || value instanceof String
                || value instanceof Character) {
            return value;
        }
        if (value instanceof Map) {
            Map<?, ?> original = (Map<?, ?>) value;
            Map<String, Object> result = new LinkedHashMap<>(original.size());
            for (Map.Entry<?, ?> entry : original.entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection) {
            Collection<?> original = (Collection<?>) value;
            List<Object> result = new ArrayList<>(original.size());
            for (Object element : original) {
                result.add(normalize(element));
            }
            return result;
        }
        if (value instanceof Object[]) {
            Object[] original = (Object[]) value;
            List<Object> result = new ArrayList<>(original.length);
            for (Object element : original) {
                result.add(normalize(element));
            }
            return result;
        }
        return value.toString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public T getValue() throws Exception {
        return callable.call();
    }
}
