package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal BlockLoaderRegistry implementation to satisfy usages in EngineApi/JsApiBootstrap/JSModifier.
 * Keeps a thread-safe list of JS loader functions (Graal Value) and invokes them on notifyEvent.
 *
 * Note: JS Value instances must be executed on the Graal owning thread; this code assumes notifyEvent
 * is called on the appropriate thread (JSModifier's comment indicates it is).
 */
public final class BlockLoaderRegistry {
    private final List<Value> loaders = new CopyOnWriteArrayList<>();

    /** Register a JS loader function (expects a callable Graal Value). */
    public void register(Value fn) {
        if (fn == null || !fn.canExecute()) {
            throw new IllegalArgumentException("register expects an executable function Value");
        }
        loaders.add(fn);
    }

    /**
     * Notify registered loaders of a block event.
     * Signature matches how JSModifier calls it: (blockId, packName, event, x, y, z)
     */
    public void notifyEvent(int blockId, String packName, String event, int x, int y, int z) {
        for (Value loader : loaders) {
            try {
                // call the JS loader with the same arg order; guard against exceptions
                loader.execute(blockId, packName, event, x, y, z);
            } catch (Throwable t) {
                System.err.println("[BlockLoaderRegistry] loader invocation failed: " + t.getMessage());
            }
        }
    }
}