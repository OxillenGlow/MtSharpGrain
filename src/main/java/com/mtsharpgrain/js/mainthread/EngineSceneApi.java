package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;

/**
 * JS-facing Scene facade. Every call crosses to the jME render thread through
 * EngineAccess; the underlying SceneApi is never invoked directly by JS.
 */
public final class EngineSceneApi {
    private final SceneApi delegate;
    private final EngineAccess engine;

    public EngineSceneApi(SceneApi delegate, EngineAccess engine) {
        this.delegate = delegate;
        this.engine = engine;
    }

    @HostAccess.Export public long createNode(String name) {
        return engine.call(() -> delegate.createNode(name));
    }
    @HostAccess.Export public long createCube(String name, float size) {
        return engine.call(() -> delegate.createCube(name, size));
    }
    @HostAccess.Export public long createRectangle(String name, float x, float y, float z) {
        return engine.call(() -> delegate.createRectangle(name, x, y, z));
    }
    @HostAccess.Export public void attachChild(long parent, long child) {
        engine.post(() -> delegate.attachChild(parent, child));
    }
    @HostAccess.Export public void setPosition(long handle, float x, float y, float z) {
        engine.post(() -> delegate.setPosition(handle, x, y, z));
    }
    @HostAccess.Export public float[] getPosition(long handle) {
        return engine.call(() -> delegate.getPosition(handle));
    }
    @HostAccess.Export public void setRotation(long handle, float x, float y, float z) {
        engine.post(() -> delegate.setRotation(handle, x, y, z));
    }
    @HostAccess.Export public float[] getRotation(long handle) {
        return engine.call(() -> delegate.getRotation(handle));
    }
    @HostAccess.Export public void setColor(long handle, float r, float g, float b, float a) {
        engine.post(() -> delegate.setColor(handle, r, g, b, a));
    }
    @HostAccess.Export public int getBlockId(int x, int y, int z) {
        return engine.call(() -> delegate.getBlockId(x, y, z));
    }
    @HostAccess.Export public long createLight(String name, float r, float g, float b, float radius) {
        return engine.call(() -> delegate.createLight(name, r, g, b, radius));
    }
    @HostAccess.Export public void setLightColor(long handle, float r, float g, float b) {
        engine.post(() -> delegate.setLightColor(handle, r, g, b));
    }
    @HostAccess.Export public void setLightRadius(long handle, float radius) {
        engine.post(() -> delegate.setLightRadius(handle, radius));
    }
    @HostAccess.Export public void destroy(long handle) {
        engine.post(() -> delegate.destroy(handle));
    }
    @HostAccess.Export public void setRelativePosition(long handle, float x, float y, float z) {
        engine.post(() -> delegate.setRelativePosition(handle, x, y, z));
    }
}
