package io.github.projectunified.faststats.featureflag;

import io.github.projectunified.faststats.core.Feature;
import io.github.projectunified.faststats.core.Submitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Feature Flag Manager that coordinates flag checks, opting in/out, caching, and TTL.
 */
public final class FeatureFlagManager extends Feature {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> fetchesInProgress = new ConcurrentHashMap<>();

    private volatile Duration ttl = DEFAULT_TTL;

    /**
     * Creates a new FeatureFlagManager.
     */
    public FeatureFlagManager() {
    }

    /**
     * Get the TTL for the cached flag values.
     *
     * @return the TTL duration
     */
    public Duration getTtl() {
        return ttl;
    }

    /**
     * Set the TTL for the cached flag values.
     *
     * @param ttl the TTL duration
     * @return this manager instance
     */
    public FeatureFlagManager ttl(Duration ttl) {
        if (ttl == null || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL cannot be null or negative");
        }
        this.ttl = ttl;
        return this;
    }

    /**
     * Returns the global error context attributes configured for this manager.
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
     * @return this manager instance
     */
    public FeatureFlagManager attributes(Consumer<Map<String, Object>> consumer) {
        if (consumer != null) {
            consumer.accept(this.attributes);
        }
        return this;
    }

    /**
     * Sets the global error context attributes using a supplier.
     *
     * @param supplier the supplier of attributes
     * @return this manager instance
     */
    public FeatureFlagManager attributes(Supplier<Map<String, Object>> supplier) {
        if (supplier != null) {
            Map<String, Object> supplied = supplier.get();
            this.attributes.clear();
            if (supplied != null) {
                this.attributes.putAll(supplied);
            }
        }
        return this;
    }

    /**
     * Defines a boolean feature flag.
     *
     * @param id           the flag id
     * @param defaultValue the default value
     * @return a new FeatureFlag
     */
    public FeatureFlag<Boolean> define(String id, boolean defaultValue) {
        return new FeatureFlag<>(id, defaultValue, null, this);
    }

    /**
     * Defines a boolean feature flag with attributes.
     *
     * @param id           the flag id
     * @param defaultValue the default value
     * @param attributes   the attributes
     * @return a new FeatureFlag
     */
    public FeatureFlag<Boolean> define(String id, boolean defaultValue, Map<String, Object> attributes) {
        return new FeatureFlag<>(id, defaultValue, attributes, this);
    }

    /**
     * Defines a String feature flag.
     *
     * @param id           the flag id
     * @param defaultValue the default value
     * @return a new FeatureFlag
     */
    public FeatureFlag<String> define(String id, String defaultValue) {
        return new FeatureFlag<>(id, defaultValue, null, this);
    }

    /**
     * Defines a String feature flag with attributes.
     *
     * @param id           the flag id
     * @param defaultValue the default value
     * @param attributes   the attributes
     * @return a new FeatureFlag
     */
    public FeatureFlag<String> define(String id, String defaultValue, Map<String, Object> attributes) {
        return new FeatureFlag<>(id, defaultValue, attributes, this);
    }

    /**
     * Defines a Number feature flag.
     *
     * @param id           the flag id
     * @param defaultValue the default value
     * @return a new FeatureFlag
     */
    public FeatureFlag<Number> define(String id, Number defaultValue) {
        return new FeatureFlag<>(id, defaultValue, null, this);
    }

    /**
     * Defines a Number feature flag with attributes.
     *
     * @param id           the flag id
     * @param defaultValue the default value
     * @param attributes   the attributes
     * @return a new FeatureFlag
     */
    public FeatureFlag<Number> define(String id, Number defaultValue, Map<String, Object> attributes) {
        return new FeatureFlag<>(id, defaultValue, attributes, this);
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> fetch(FeatureFlag<T> flag) {
        synchronized (fetchesInProgress) {
            CompletableFuture<?> existing = fetchesInProgress.get(flag.getId());
            if (existing != null) {
                return (CompletableFuture<T>) existing;
            }
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, Object> requestBody = new LinkedHashMap<>();
                    requestBody.put("key", flag.getId());

                    Map<String, Object> mergedAttributes = new LinkedHashMap<>(this.attributes);
                    if (flag.attributes() != null) {
                        mergedAttributes.putAll(flag.attributes());
                    }
                    if (!mergedAttributes.isEmpty()) {
                        requestBody.put("attributes", mergedAttributes);
                    }

                    String url = getFullUrl("/v1/check");
                    Submitter.Response response = submit(url, requestBody, false);
                    if (response.getException().isPresent()) {
                        throw response.getException().get();
                    }
                    if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                        throw new IllegalStateException("Unexpected response status: " + response.getStatusCode());
                    }
                    String responseStr = response.readString();
                    Map<String, Object> responseMap = deserialize(responseStr);

                    Object rawValue = responseMap.get("value");
                    if (rawValue == null) {
                        throw new IllegalStateException("Missing or invalid 'value' in response: " + responseStr);
                    }

                    T parsedValue = castValue(flag, rawValue);
                    flag.setLastFetch(System.currentTimeMillis());
                    flag.setValue(parsedValue);
                    return parsedValue;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to fetch feature flag: " + flag.getId(), e);
                }
            });

            future.whenComplete((v, t) -> {
                synchronized (fetchesInProgress) {
                    fetchesInProgress.remove(flag.getId());
                }
            });

            fetchesInProgress.put(flag.getId(), future);
            return future;
        }
    }

    public <T> CompletableFuture<T> optIn(FeatureFlag<T> flag) {
        return sendOptRequest(flag, "/v1/opt-in");
    }

    public <T> CompletableFuture<T> optOut(FeatureFlag<T> flag) {
        return sendOptRequest(flag, "/v1/opt-out");
    }

    private <T> CompletableFuture<T> sendOptRequest(FeatureFlag<T> flag, String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("flag", flag.getId());

                String url = getFullUrl(path);
                Submitter.Response response = submit(url, requestBody, false);
                if (response.getException().isPresent()) {
                    throw response.getException().get();
                }
                if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                    throw new IllegalStateException("Unexpected response status: " + response.getStatusCode());
                }
                return fetch(flag).get();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to opt request: " + flag.getId(), e);
            }
        });
    }

    private String getFullUrl(String path) {
        String flagsServer = System.getProperty("faststats.flags-server");
        if (flagsServer == null) {
            flagsServer = "https://flags.faststats.dev";
        }
        if (flagsServer.endsWith("/") && path.startsWith("/")) {
            return flagsServer + path.substring(1);
        } else if (!flagsServer.endsWith("/") && !path.startsWith("/")) {
            return flagsServer + "/" + path;
        } else {
            return flagsServer + path;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T castValue(FeatureFlag<T> flag, Object rawValue) {
        switch (flag.getType()) {
            case STRING:
                return (T) String.valueOf(rawValue);
            case BOOLEAN:
                if (rawValue instanceof Boolean) {
                    return (T) rawValue;
                }
                return (T) Boolean.valueOf(String.valueOf(rawValue));
            case NUMBER:
                if (rawValue instanceof Number) {
                    return (T) rawValue;
                }
                try {
                    return (T) Double.valueOf(String.valueOf(rawValue));
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Expected a number but got: " + rawValue, e);
                }
            default:
                throw new IllegalStateException("Unknown flag type: " + flag.getType());
        }
    }
}
