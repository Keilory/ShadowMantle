package com.keilory.shadowmantle.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared worker scheduler. Minecraft/client API access must stay on the client thread. */
public final class ShadowMantleScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("shadowmantle/Scheduler");
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ShadowMantle-Worker-" + THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failedThread, error) ->
                    LOGGER.error("Unhandled exception on {}", failedThread.getName(), error));
            return thread;
        }
    });

    public void execute(Runnable task) { executor.execute(task); }
    public void execute(String taskName, Runnable task) { execute(named(taskName, task)); }
    public Future<?> submit(Runnable task) { return executor.submit(task); }
    public Future<?> submit(String taskName, Runnable task) { return executor.submit(named(taskName, task)); }
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) { return executor.schedule(task, delay, unit); }
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return executor.scheduleAtFixedRate(task, initialDelay, period, unit);
    }
    private Runnable named(String taskName, Runnable task) {
        if (taskName == null || taskName.isBlank()) throw new IllegalArgumentException("taskName");
        if (task == null) throw new IllegalArgumentException("task");
        return () -> { Thread thread = Thread.currentThread(); String previous = thread.getName(); thread.setName("ShadowMantle-" + taskName);
            try { task.run(); } finally { thread.setName(previous); } };
    }
    public void shutdown() { executor.shutdownNow(); }
}
