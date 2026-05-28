package io.github.projectunified.faststats.core;

public class MockTaskScheduler implements TaskScheduler {
    Runnable scheduledTask;
    long capturedInitialDelay;
    long capturedPeriod;
    boolean shutdownCalled = false;

    @Override
    public void schedule(Runnable task, long initialDelayMs, long periodMs) {
        this.scheduledTask = task;
        this.capturedInitialDelay = initialDelayMs;
        this.capturedPeriod = periodMs;
    }

    @Override
    public void shutdown() {
        this.shutdownCalled = true;
    }
}
