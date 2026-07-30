package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;

/** JS-facing GUI facade; all calls that read or mutate GUI state use the engine gateway. */
public final class EngineGuiApi {
    private final GuiApi delegate;
    private final EngineAccess engine;

    public EngineGuiApi(GuiApi delegate, EngineAccess engine) {
        this.delegate = delegate;
        this.engine = engine;
    }

    @HostAccess.Export
    public long guiWord(String word, float x, float y, float z, float sizePixels, String tag) {
        return engine.call(() -> delegate.guiWord(word, x, y, z, sizePixels, tag));
    }
    @HostAccess.Export
    public long getHandleByTag(String tag) {
        return engine.call(() -> delegate.getHandleByTag(tag));
    }
    @HostAccess.Export
    public void setColor(long handle, float r, float g, float b, float a) {
        engine.run(() -> delegate.setColor(handle, r, g, b, a));
    }
    @HostAccess.Export
    public void removeWord(long handle) {
        engine.run(() -> delegate.removeWord(handle));
    }
    @HostAccess.Export
    public void toTop(long handle) {
        engine.run(() -> delegate.toTop(handle));
    }
    @HostAccess.Export
    public void toBottom(long handle) {
        engine.run(() -> delegate.toBottom(handle));
    }
    @HostAccess.Export
    public boolean getDraw() {
        return engine.call(delegate::getDraw);
    }
    @HostAccess.Export
    public String getGuiState() {
        return engine.call(delegate::getGuiState);
    }
}