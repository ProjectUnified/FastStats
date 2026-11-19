package dev.faststats.featureflag;

import dev.faststats.Attributes;
import dev.faststats.FastStatsService;
import dev.faststats.SimpleContext;
import org.jetbrains.annotations.Contract;

import java.time.Duration;
import java.util.function.Function;

/**
 * A service for managing feature flags.
 * <p>
 * Attach it with {@code contextBuilder.service(FeatureFlagService.create())}
 * and read it back with {@code context.service(FeatureFlagService.class)}.
 *
 * @since 0.24.0
 */
public interface FeatureFlagService extends FastStatsService {
    /**
     * Define a boolean feature flag.
     *
     * @param id           the flag identifier
     * @param defaultValue the default value
     * @return the feature flag
     * @since 0.24.0
     */
    @Contract(value = "_, _ -> new", pure = true)
    FeatureFlag<Boolean> define(String id, boolean defaultValue);

    /**
     * Define a boolean feature flag with per-flag targeting attributes.
     *
     * @param id           the flag identifier
     * @param defaultValue the default value
     * @param attributes   the per-flag targeting attributes, merged with the service attributes
     * @return the feature flag
     * @since 0.24.0
     */
    @Contract(value = "_, _, _ -> new", pure = true)
    FeatureFlag<Boolean> define(String id, boolean defaultValue, Attributes attributes);

    /**
     * Define a string feature flag.
     *
     * @param id           the flag identifier
     * @param defaultValue the default value
     * @return the feature flag
     * @since 0.24.0
     */
    @Contract(value = "_, _ -> new", pure = true)
    FeatureFlag<String> define(String id, String defaultValue);

    /**
     * Define a string feature flag with per-flag targeting attributes.
     *
     * @param id           the flag identifier
     * @param defaultValue the default value
     * @param attributes   the per-flag targeting attributes, merged with the service attributes
     * @return the feature flag
     * @since 0.24.0
     */
    @Contract(value = "_, _, _ -> new", pure = true)
    FeatureFlag<String> define(String id, String defaultValue, Attributes attributes);

    /**
     * Define a number feature flag.
     *
     * @param id           the flag identifier
     * @param defaultValue the default value
     * @return the feature flag
     * @since 0.24.0
     */
    @Contract(value = "_, _ -> new", pure = true)
    FeatureFlag<Number> define(String id, Number defaultValue);

    /**
     * Define a number feature flag with per-flag targeting attributes.
     *
     * @param id           the flag identifier
     * @param defaultValue the default value
     * @param attributes   the per-flag targeting attributes, merged with the service attributes
     * @return the feature flag
     * @since 0.24.0
     */
    @Contract(value = "_, _, _ -> new", pure = true)
    FeatureFlag<Number> define(String id, Number defaultValue, Attributes attributes);

    /**
     * Returns the global targeting attributes configured for this service.
     * <p>
     * These attributes apply to every flag defined by the service and are
     * merged with any per-flag attributes supplied during definition.
     *
     * @return the global targeting attributes
     * @since 0.24.0
     */
    @Contract(pure = true)
    Attributes getAttributes();

    /**
     * Returns the cache time-to-live used for resolved flag values.
     *
     * @return the configured cache time-to-live
     * @since 0.24.0
     */
    Duration getTTL();

    /**
     * A feature flag service factory.
     *
     * @since 0.24.0
     */
    interface Factory {
        /**
         * Sets the global targeting attributes for services created by this factory.
         * <p>
         * These attributes apply to every flag defined by the service and are
         * merged with any per-flag attributes supplied during definition.
         *
         * @param attributes the global targeting attributes
         * @return the feature flag service factory
         * @since 0.24.0
         */
        @Contract(mutates = "this")
        Factory attributes(Attributes attributes);

        /**
         * Sets the cache time-to-live for resolved flag values.
         *
         * @param ttl the cache time-to-live for resolved flag values
         * @return the feature flag service factory
         * @throws IllegalArgumentException if the TTL is negative
         * @since 0.24.0
         */
        @Contract(mutates = "this")
        Factory ttl(Duration ttl) throws IllegalArgumentException;

        /**
         * Creates a new feature flag service.
         *
         * @return the feature flag service
         * @throws IllegalArgumentException if the TTL is negative
         * @since 0.24.0
         */
        @Contract(value = " -> new", pure = true)
        FeatureFlagService create() throws IllegalArgumentException;
    }

    /**
     * Creates a factory for a feature flag service with the default attributes and TTL.
     *
     * @return a service factory for {@link dev.faststats.FastStatsContext.Builder#service(Function)}
     * @since 0.33.0
     */
    @Contract(value = " -> new", pure = true)
    static Function<SimpleContext, FeatureFlagService> create() {
        return context -> new SimpleFeatureFlagService.Factory(context).create();
    }

    /**
     * Creates a factory for a feature flag service.
     *
     * @param attributes the global targeting attributes
     * @param ttl        the cache time-to-live
     * @return a service factory for {@link dev.faststats.FastStatsContext.Builder#service(Function)}
     * @throws IllegalArgumentException if the TTL is negative
     * @since 0.33.0
     */
    @Contract(value = "_, _ -> new", pure = true)
    static Function<SimpleContext, FeatureFlagService> create(final Attributes attributes, final Duration ttl) {
        return context -> new SimpleFeatureFlagService.Factory(context)
                .attributes(attributes)
                .ttl(ttl)
                .create();
    }
}
