package io.github.projectunified.faststats.core;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Represents a single telemetry data source.
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
     */
    static Metric<String> string(final String name, final Callable<String> supplier) {
        return new Metric<String>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getValue() throws Exception {
                return supplier.call();
            }
        };
    }

    /**
     * Creates a new Number metric.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the value
     * @return a new Metric instance returning a Number
     */
    static Metric<Number> number(final String name, final Callable<Number> supplier) {
        return new Metric<Number>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Number getValue() throws Exception {
                return supplier.call();
            }
        };
    }

    /**
     * Creates a new Boolean metric.
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the value
     * @return a new Metric instance returning a Boolean
     */
    static Metric<Boolean> bool(final String name, final Callable<Boolean> supplier) {
        return new Metric<Boolean>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Boolean getValue() throws Exception {
                return supplier.call();
            }
        };
    }

    /**
     * Creates a new Map metric.
     * Entry values are automatically normalized to standard supported types (Boolean, Number, String).
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the map
     * @param <V>      the original value type of the map
     * @return a new Metric instance returning a normalized Map
     */
    static <V> Metric<Map<String, Object>> map(final String name, final Callable<Map<String, V>> supplier) {
        return new Metric<Map<String, Object>>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Map<String, Object> getValue() throws Exception {
                Map<String, V> original = supplier.call();
                if (original == null) {
                    return null;
                }
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<String, V> entry : original.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof Boolean || val instanceof Number) {
                        result.put(entry.getKey(), val);
                    } else if (val != null) {
                        result.put(entry.getKey(), val.toString());
                    } else {
                        result.put(entry.getKey(), null);
                    }
                }
                return result;
            }
        };
    }

    /**
     * Creates a new Collection metric.
     * Elements are automatically normalized to standard supported types (Boolean, Number, String).
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the collection
     * @param <E>      the original element type of the collection
     * @return a new Metric instance returning a normalized Collection
     */
    static <E> Metric<Collection<Object>> collection(final String name, final Callable<Collection<E>> supplier) {
        return new Metric<Collection<Object>>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Collection<Object> getValue() throws Exception {
                Collection<E> original = supplier.call();
                if (original == null) {
                    return null;
                }
                List<Object> result = new ArrayList<>(original.size());
                for (E element : original) {
                    if (element instanceof Boolean || element instanceof Number) {
                        result.add(element);
                    } else if (element != null) {
                        result.add(element.toString());
                    } else {
                        result.add(null);
                    }
                }
                return result;
            }
        };
    }

    /**
     * Creates a new Array metric.
     * Elements are automatically normalized to standard supported types (Boolean, Number, String).
     *
     * @param name     the name of the metric
     * @param supplier the callable providing the array
     * @param <E>      the original element type of the array
     * @return a new Metric instance returning a normalized Array
     */
    static <E> Metric<Object[]> array(final String name, final Callable<E[]> supplier) {
        return new Metric<Object[]>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Object[] getValue() throws Exception {
                E[] original = supplier.call();
                if (original == null) {
                    return null;
                }
                Object[] result = new Object[original.length];
                for (int i = 0; i < original.length; i++) {
                    E element = original[i];
                    if (element instanceof Boolean || element instanceof Number) {
                        result[i] = element;
                    } else if (element != null) {
                        result[i] = element.toString();
                    } else {
                        result[i] = null;
                    }
                }
                return result;
            }
        };
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
