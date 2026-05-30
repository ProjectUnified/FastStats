package io.github.projectunified.faststats.core;

public class MockTaskScheduler implements TaskScheduler {
    Runnable scheduledTask;
    long capturedInitialDelay;
    long capturedPeriod;
    boolean shutdownCalled = false;

    boolean taskCancelled = false;

    @Override
    public TaskScheduler.Task schedule(Runnable task, long initialDelayMs, long periodMs) {
        this.scheduledTask = task;
        this.capturedInitialDelay = initialDelayMs;
        this.capturedPeriod = periodMs;
        return () -> taskCancelled = true;
    }

    @Override
    public void shutdown() {
        this.shutdownCalled = true;
    }
}
