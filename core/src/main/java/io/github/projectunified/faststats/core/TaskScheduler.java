package io.github.projectunified.faststats.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
            public synchronized void schedule(Runnable task, long initialDelayMs, long periodMs) {
                if (executor != null) {
                    return;
                }
                executor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "faststats-submitter");
                    thread.setDaemon(true);
                    return thread;
                });
                executor.scheduleAtFixedRate(task, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
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
     */
    void schedule(Runnable task, long initialDelayMs, long periodMs);

    /**
     * Shuts down the scheduler, terminating any active scheduled task.
     */
    void shutdown();
}
