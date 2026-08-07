package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;

/**
 * Small JS-facing Engine object that exposes lifecycle constants and
 * registerLoader(fn) that delegates to the pack's BlockLoaderRegistry.
 *
 * Construct a per-pack EngineApi with its BlockLoaderRegistry instance.
 */
public final class EngineApi {
    private final BlockLoaderRegistry loaderRegistry;

    public EngineApi(BlockLoaderRegistry loaderRegistry) {
        this.loaderRegistry = loaderRegistry;
    }

    @HostAccess.Export
    public void registerLoader(Object fn) {
        if (fn instanceof org.graalvm.polyglot.Value v && v.canExecute()) {
            loaderRegistry.register(v);
        } else {
            throw new IllegalArgumentException("registerLoader expects a function");
        }
    }

    @HostAccess.Export
    public String LOADED() { return "LOADED"; }
    @HostAccess.Export
    public String PLACED() { return "PLACED"; }
    @HostAccess.Export
    public String DESTROYED() { return "DESTROYED"; }
}
