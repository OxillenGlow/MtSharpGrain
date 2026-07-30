package com.mtsharpgrain.js.mainthread;

import com.jme3.app.SimpleApplication;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Single gateway from a mod virtual thread to jMonkeyEngine state.
 *
 * <p>The render thread is captured when Main creates this object. Calls made
 * from that thread execute directly; calls made by a mod virtual thread are
 * queued through {@link SimpleApplication#enqueue(Callable)} and wait on the
 * returned Future. That wait suspends the virtual thread, not the game loop.
 */
public final class EngineAccess {

    private final SimpleApplication app;
    private final Thread mainThread;

    public EngineAccess(SimpleApplication app) {
        this.app = app;
        this.mainThread = Thread.currentThread();
    }

    public boolean isMainThread() {
        return Thread.currentThread() == mainThread;
    }

    public <T> T call(Callable<T> callable) {
        if (isMainThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw propagate(e);
            }
        }

        try {
            Future<T> future = app.enqueue(callable);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for the render thread", e);
        } catch (ExecutionException e) {
            throw propagate(e.getCause());
        }
    }

    public void run(Runnable runnable) {
        call(() -> {
            runnable.run();
            return null;
        });
    }

    private static RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException(error);
    }
}