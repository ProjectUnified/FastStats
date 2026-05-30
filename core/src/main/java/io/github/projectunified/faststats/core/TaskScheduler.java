package io.github.projectunified.faststats.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Interface representing the task scheduling mechanism for executing recurring telemetry submissions.
 */
public interface TaskScheduler {
    /**
     * Creates a default single-threaded daemon executor-based scheduler.
     *
     * @return the default task scheduler instance
     */
    static TaskScheduler defaultScheduler() {
        return new TaskScheduler() {
            private ScheduledExecutorService executor;

            @Override
            public synchronized Task schedule(Runnable task, long initialDelayMs, long periodMs) {
                if (executor == null) {
                    executor = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread thread = new Thread(r, "faststats-thread");
                        thread.setDaemon(true);
                        return thread;
                    });
                }
                ScheduledFuture<?> future = executor.scheduleAtFixedRate(task, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
                return () -> future.cancel(true);
            }

            @Override
            public synchronized void shutdown() {
                if (executor != null) {
                    executor.shutdown();
                    executor = null;
                }
            }
        };
    }

    /**
     * Schedules the task for periodic execution.
     *
     * @param task           the telemetry submission task
     * @param initialDelayMs the initial delay in milliseconds before the first execution
     * @param periodMs       the period between consecutive executions in milliseconds
     * @return the scheduled task instance
     */
    Task schedule(Runnable task, long initialDelayMs, long periodMs);

    /**
     * Shuts down the scheduler, terminating any active scheduled task.
     */
    void shutdown();

    /**
     * Interface representing a scheduled task.
     */
    interface Task {
        /**
         * Cancels the scheduled task.
         */
        void cancel();
    }
}
