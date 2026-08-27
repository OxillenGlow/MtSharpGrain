package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.util.*;
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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TimerScheduler-" + nextTimerId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Schedule a repeating callback (setInterval).
     * @param callback JS function to call
     * @param intervalMs Interval in milliseconds
     * @return timerId (for clearInterval)
     */
    @HostAccess.Export
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
     * @return timerId id of timer for closing
     */
    @HostAccess.Export
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
    @HostAccess.Export
    public void clearInterval(long timerId) {
        ScheduledFuture<?> future = activeTimers.remove(timerId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void shutdown() {
        for (ScheduledFuture<?> future : activeTimers.values()) {
            future.cancel(false);
        }
        activeTimers.clear();
        scheduler.shutdown();
    }
}
