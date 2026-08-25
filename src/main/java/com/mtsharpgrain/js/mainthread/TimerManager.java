package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.Value;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages setInterval/setTimeout-style timers for a single mod pack.
 * All callbacks are executed on the mod's virtual thread.
 */
public final class TimerManager {
    private final Map<Long, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
    private final AtomicLong nextTimerId = new AtomicLong(1);
    private final ScheduledExecutorService scheduler;
    private final ModBridge bridge;

    public TimerManager(ModBridge bridge) {
        this.bridge = bridge;
        // Single-thread scheduler to avoid contention
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TimerScheduler-" + nextTimerId.getAndIncrement());
            thread.setDaemon(true); // Don't block JVM shutdown
            return thread;
        });
    }

    /**
     * Schedule a repeating callback (setInterval).
     * @param callback JS function to call
     * @param intervalMs Interval in milliseconds
     * @return Timer ID (for clearInterval)
     */
    public long setInterval(Value callback, long intervalMs) {
        long timerId = nextTimerId.getAndIncrement();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> bridge.submitTask(() -> {
                try {
                    callback.execute();
                } catch (Exception e) {
                    System.err.println("[TimerManager] Interval callback failed: " + e.getMessage());
                }
            }),
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        );
        activeTimers.put(timerId, future);
        return timerId;
    }

    /**
     * Schedule a one-time callback (setTimeout).
     * @param callback JS function to call
     * @param delayMs Delay in milliseconds
     * @return Timer ID
     */
    public long setTimeout(Value callback, long delayMs) {
        long timerId = nextTimerId.getAndIncrement();
        ScheduledFuture<?> future = scheduler.schedule(
            () -> bridge.submitTask(() -> {
                try {
                    callback.execute();
                } catch (Exception e) {
                    System.err.println("[TimerManager] Timeout callback failed: " + e.getMessage());
                }
            }),
            delayMs,
            TimeUnit.MILLISECONDS
        );
        activeTimers.put(timerId, future);
        return timerId;
    }

    /**
     * Cancel a timer (clearInterval/clearTimeout).
     * @param timerId ID returned by setInterval/setTimeout
     */
    public void clearInterval(long timerId) {
        ScheduledFuture<?> future = activeTimers.remove(timerId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** Shutdown all timers (called when mod unloads). */
    public void shutdown() {
        for (ScheduledFuture<?> future : activeTimers.values()) {
            future.cancel(false);
        }
        activeTimers.clear();
        scheduler.shutdown();
    }
}
