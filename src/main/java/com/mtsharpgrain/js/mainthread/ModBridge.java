package com.mtsharpgrain.js.mainthread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe mailbox for one mod pack.
 *
 * <p>Only the pack's virtual thread consumes this queue. The render thread and
 * other packs may submit work without touching the pack's Graal Context.
 * Callable results are completed by the owning virtual thread, which means a
 * JS callback can safely perform another EngineAccess call while handling it.
 */
public final class ModBridge {

    private static final Runnable POISON = () -> { };

    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private volatile boolean accepting = true;
    private volatile boolean shutdownRequested;

    public void submitTask(Runnable task) {
        if (task == null || !accepting) return;
        tasks.offer(task);
    }

    public <T> CompletableFuture<T> submitCallable(Callable<T> callable) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!accepting) {
            result.completeExceptionally(new IllegalStateException("Mod bridge is shut down"));
            return result;
        }
        tasks.offer(() -> {
            try {
                result.complete(callable.call());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    Runnable takeTask() throws InterruptedException {
        return tasks.take();
    }

    boolean shouldStop() {
        return shutdownRequested && tasks.isEmpty();
    }

    /**
     * Stops accepting new work and places a sentinel behind already queued
     * tasks. The owning loop exits after the queued shutdown work is complete.
     */
    public void requestShutdown() {
        accepting = false;
        shutdownRequested = true;
        tasks.offer(POISON);
    }

    boolean isPoison(Runnable task) {
        return task == POISON;
    }
}