package io.github.projectunified.faststats.featureflag;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A feature flag representing a setting or configuration fetched from the FastStats server.
 *
 * @param <T> the type of the flag value (String, Number, or Boolean)
 */
public final class FeatureFlag<T> {
    private final String id;
    private final T defaultValue;
    private final Map<String, Object> attributes;
    private final Type type;
    private final FeatureFlagManager manager;

    private volatile T value;
    private volatile Long lastFetch;

    FeatureFlag(String id, T defaultValue, Map<String, Object> attributes, FeatureFlagManager manager) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.attributes = attributes;
        this.manager = manager;

        if (defaultValue instanceof String) {
            this.type = Type.STRING;
        } else if (defaultValue instanceof Number) {
            this.type = Type.NUMBER;
        } else if (defaultValue instanceof Boolean) {
            this.type = Type.BOOLEAN;
        } else {
            throw new IllegalArgumentException("Unsupported type: " + defaultValue.getClass().getName());
        }
    }

    /**
     * Get the flag identifier.
     *
     * @return the flag id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the type representing the value type of this flag.
     *
     * @return the value type
     */
    public Type getType() {
        return type;
    }

    /**
     * Get the default value for this flag.
     *
     * @return the default value
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Returns the attributes configured for this flag.
     *
     * @return the attributes map
     */
    Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * Set the current cached value and update last fetch timestamp.
     *
     * @param value the new value
     */
    void setValue(T value) {
        this.value = value;
    }

    /**
     * Sets the timestamp of the last successful fetch.
     *
     * @param lastFetch timestamp in milliseconds
     */
    void setLastFetch(Long lastFetch) {
        this.lastFetch = lastFetch;
    }

    /**
     * Get the current cached flag value.
     *
     * @return the cached value, if present
     */
    public Optional<T> getCached() {
        return Optional.ofNullable(value);
    }

    /**
     * Get the expiration time for the current cached value.
     *
     * @return the expiration time, or empty if not fetched yet
     */
    public Optional<Instant> getExpiration() {
        Long last = this.lastFetch;
        if (last == null) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(last).plus(manager.getTtl()));
    }

    /**
     * Returns whether the current cached value is expired or absent.
     *
     * @return true if expired or absent, false otherwise
     */
    public boolean isExpired() {
        Long last = this.lastFetch;
        if (last == null) {
            return true;
        }
        return System.currentTimeMillis() - last > manager.getTtl().toMillis();
    }

    /**
     * Returns whether the current cached value is still valid.
     *
     * @return true if valid and not expired, false otherwise
     */
    public boolean isValid() {
        return value != null && !isExpired();
    }

    /**
     * Return a future that completes with the flag value once it is ready.
     *
     * @return a future completing with the flag value
     */
    public CompletableFuture<T> whenReady() {
        T cached = value;
        if (cached == null || isExpired()) {
            return fetch();
        }
        return CompletableFuture.completedFuture(cached);
    }

    /**
     * Force a fresh fetch of the flag value from the server.
     *
     * @return a future completing with the latest server value
     */
    public CompletableFuture<T> fetch() {
        return manager.fetch(this);
    }

    /**
     * Request that the server opt in to this flag, then refresh the cached value.
     *
     * @return a future completing with the updated flag value
     */
    public CompletableFuture<T> optIn() {
        return manager.optIn(this);
    }

    /**
     * Request that the server opt out of this flag, then refresh the cached value.
     *
     * @return a future completing with the updated flag value
     */
    public CompletableFuture<T> optOut() {
        return manager.optOut(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeatureFlag<?> that = (FeatureFlag<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FeatureFlag{id='" + id + "'}";
    }

    /**
     * Supported value types for feature flags.
     */
    public enum Type {
        STRING,
        BOOLEAN,
        NUMBER
    }
}
