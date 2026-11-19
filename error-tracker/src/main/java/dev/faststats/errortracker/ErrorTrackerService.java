package dev.faststats.errortracker;

import dev.faststats.FastStatsService;
import dev.faststats.SimpleContext;
import org.jetbrains.annotations.Contract;

import java.util.function.Function;

/**
 * A service for managing error trackers.
 * <p>
 * Attach it with {@code contextBuilder.service(ErrorTrackerService.create(ErrorTracker.contextAware()))}
 * and read it back with {@code context.service(ErrorTrackerService.class)}.
 *
 * @since 0.24.0
 */
public interface ErrorTrackerService extends FastStatsService {
    /**
     * Returns the global/internal error tracker configured for this service.
     *
     * @return the global/internal error tracker
     * @since 0.24.0
     */
    @Contract(pure = true)
    ErrorTracker globalErrorTracker();

    /**
     * Registers an additional error tracker for submission with this service.
     * <p>
     * Additional trackers registered here are submitted by the same context, but are not
     * used for internal FastStats errors.
     *
     * @param errorTracker the additional error tracker
     * @return this service
     * @since 0.24.0
     */
    @Contract(value = "_ -> this", mutates = "this")
    ErrorTrackerService registerErrorTracker(ErrorTracker errorTracker);

    /**
     * Creates a factory for an error tracker service.
     *
     * @param globalErrorTracker the global/internal error tracker
     * @return a service factory for {@link dev.faststats.FastStatsContext.Builder#service(Function)}
     * @since 0.33.0
     */
    @Contract(value = "_ -> new", pure = true)
    static Function<SimpleContext, ErrorTrackerService> create(final ErrorTracker globalErrorTracker) {
        return context -> new SimpleErrorTrackerService(context, globalErrorTracker);
    }

    @Override
    default void onError(final Throwable error) {
        final ErrorTracker tracker = globalErrorTracker();
        if (tracker instanceof SimpleErrorTracker) {
            final ClassLoader loader = ((SimpleErrorTracker) tracker).attachedLoader();
            if (loader != null && !ErrorTracker.isSameLoader(loader, error)) return;
        }
        tracker.trackError(error);
    }
}
