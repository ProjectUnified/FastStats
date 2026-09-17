package io.github.projectunified.faststats.core;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Represents a single telemetry data source.
 * <p>
 * Metric names must match {@code ^[a-z0-9_]+$}; invalid names are rejected when
 * the metric is created. Values are normalized to types supported by the
 * telemetry payload, see {@link SimpleMetric#normalize(Object)}.
 *
 * @param <T> the type of value this metric holds
 */
public interface Metric<T> {
    /**
     * Creates a new String metric.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the value
     * @return a new Metric instance returning a String
     * @throws IllegalArgumentException if the name is invalid
     */
    static Metric<String> string(final String name, final Callable<String> supplier) {
        return new SimpleMetric<>(name, supplier);
    }

    /**
     * Creates a new Number metric.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the value
     * @return a new Metric instance returning a Number
     * @throws IllegalArgumentException if the name is invalid
     */
    static Metric<Number> number(final String name, final Callable<Number> supplier) {
        return new SimpleMetric<>(name, supplier);
    }

    /**
     * Creates a new Boolean metric.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the value
     * @return a new Metric instance returning a Boolean
     * @throws IllegalArgumentException if the name is invalid
     */
    static Metric<Boolean> bool(final String name, final Callable<Boolean> supplier) {
        return new SimpleMetric<>(name, supplier);
    }

    /**
     * Creates a new Map metric.
     * Entry values are normalized to standard supported types (Boolean, Number, String),
     * nested maps, collections and arrays are normalized recursively.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the map
     * @param <V>      the original value type of the map
     * @return a new Metric instance returning a normalized Map
     * @throws IllegalArgumentException if the name is invalid
     */
    static <V> Metric<Map<String, Object>> map(final String name, final Callable<Map<String, V>> supplier) {
        return new SimpleMetric<>(name, () -> {
            Map<String, V> original = supplier.call();
            if (original == null) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>(original.size());
            for (Map.Entry<String, V> entry : original.entrySet()) {
                result.put(entry.getKey(), SimpleMetric.normalize(entry.getValue()));
            }
            return result;
        });
    }

    /**
     * Creates a new Collection metric.
     * Elements are normalized to standard supported types (Boolean, Number, String),
     * nested maps, collections and arrays are normalized recursively.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the collection
     * @param <E>      the original element type of the collection
     * @return a new Metric instance returning a normalized Collection
     * @throws IllegalArgumentException if the name is invalid
     */
    static <E> Metric<Collection<Object>> collection(final String name, final Callable<Collection<E>> supplier) {
        return new SimpleMetric<>(name, () -> {
            Collection<E> original = supplier.call();
            if (original == null) {
                return null;
            }
            List<Object> result = new ArrayList<>(original.size());
            for (E element : original) {
                result.add(SimpleMetric.normalize(element));
            }
            return result;
        });
    }

    /**
     * Creates a new Array metric.
     * Elements are normalized to standard supported types (Boolean, Number, String),
     * nested maps, collections and arrays are normalized recursively.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the array
     * @param <E>      the original element type of the array
     * @return a new Metric instance returning a normalized Array
     * @throws IllegalArgumentException if the name is invalid
     */
    static <E> Metric<Object[]> array(final String name, final Callable<E[]> supplier) {
        return new SimpleMetric<>(name, () -> {
            E[] original = supplier.call();
            if (original == null) {
                return null;
            }
            Object[] result = new Object[original.length];
            for (int i = 0; i < original.length; i++) {
                result[i] = SimpleMetric.normalize(original[i]);
            }
            return result;
        });
    }

    /**
     * Gets the unique name identifying this metric.
     *
     * @return the metric name
     */
    String getName();

    /**
     * Retrieves the current value of the metric.
     *
     * @return the collected value
     * @throws Exception if data collection fails
     */
    T getValue() throws Exception;
}
