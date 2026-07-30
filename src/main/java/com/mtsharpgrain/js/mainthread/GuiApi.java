package com.mtsharpgrain.js.mainthread;

import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.math.ColorRGBA;
import org.graalvm.polyglot.HostAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JS-driven GUI text elements, drawn each frame via draw(IGui) — modeled
 * directly on Master.tic()'s text-drawing pattern.
 *
 * tag  -> identifies which Engine.onTick(fn, tag) callback handles clicks
 *         on this element. guiWord() upserts by tag (re-calling it from a
 *         script updates the existing element instead of duplicating it).
 * handle -> the JS-facing identity for mutation (setColor/remove/toTop/toBottom),
 *         same handle-not-tag convention as NodeRegistry.
 *
 * clickRegistered: true = no pending click (idle/consumed). False = a click
 * just landed on this element and hasn't been dispatched to its tick(tag)
 * callback yet. Main's render-loop snippet below drains these each frame.
 *
 * All methods (including draw) must run on the render/main thread.
 */
public class GuiApi {

    public static final class GuiElement {
        final long handle;
        final String tag;
        String word;
        float x, y;
        float sizePixels;
        ColorRGBA color = ColorRGBA.Blue; // matches Master's default
        boolean clickRegistered = true;   // true = idle, false = pending click

        GuiElement(long handle, String tag, String word, float x, float y, float sizePixels) {
            this.handle = handle;
            this.tag = tag;
            this.word = word;
            this.x = x; this.y = y;
            this.sizePixels = sizePixels;
        }
    }

    private final List<GuiElement> drawOrder = new ArrayList<>();
    private final Map<Long, GuiElement> byHandle = new HashMap<>();
    private final Map<String, GuiElement> byTag = new HashMap<>();
    private final AtomicLong nextHandle = new AtomicLong(1);
    private final ReentrantLock lock = new ReentrantLock();
    private boolean canDraw = false;
    
    // ── JS-facing: create/update ────────────────────────────────────────────

    @HostAccess.Export
    public long guiWord(String word, float x, float y, float z, float sizePixels, String tag) {
        lock.lock();
        try {
        GuiElement existing = byTag.get(tag);
        if (existing != null) {
            existing.word = word;
            existing.x = x; existing.y = y;
            existing.sizePixels = sizePixels;
            return existing.handle;
        }
        GuiElement el = new GuiElement(nextHandle.getAndIncrement(), tag, word, x, y, sizePixels);
        drawOrder.add(el);
        byHandle.put(el.handle, el);
        byTag.put(tag, el);
        return el.handle;
        } finally {
            lock.unlock();
        }
    }

    @HostAccess.Export
    public long getHandleByTag(String tag) {
        lock.lock();
        try {
        GuiElement el = byTag.get(tag);
        return el == null ? -1L : el.handle;
        } finally {
            lock.unlock();
        }
    }

    // ── JS-facing: mutation by handle ───────────────────────────────────────

    @HostAccess.Export
    public void setColor(long handle, float r, float g, float b, float a) {
        lock.lock();
        try {
        GuiElement el = byHandle.get(handle);
        if (el != null) el.color = new ColorRGBA(r, g, b, a);
        } finally {
            lock.unlock();
        }
    }

    @HostAccess.Export
    public void removeWord(long handle) {
        lock.lock();
        try {
        GuiElement el = byHandle.remove(handle);
        if (el != null) {
            drawOrder.remove(el);
            byTag.remove(el.tag);
        }
        } finally {
            lock.unlock();
        }
    }

    @HostAccess.Export
    public void toTop(long handle) {
        lock.lock();
        try {
        GuiElement el = byHandle.get(handle);
        if (el == null) return;
        drawOrder.remove(el);
        drawOrder.add(el); // drawn last = on top, same convention as before
        } finally {
            lock.unlock();
        }
    }

    @HostAccess.Export
    public void toBottom(long handle) {
        lock.lock();
        try {
        GuiElement el = byHandle.get(handle);
        if (el == null) return;
        drawOrder.remove(el);
        drawOrder.add(0, el);
        } finally {
            lock.unlock();
        }
    }

    // ── Java-facing: draw + click drain (NOT exported to JS) ───────────────

    /** Copies Master.tic()'s push/font/size/color/align/text call shape. */
    public void draw(IGui gui) {
        lock.lock();
        try {
        if (canDraw) {
            List<GuiElement> snapshot = new ArrayList<>(drawOrder);
            gui.push(false);
            gui.textFont("Interface/Fonts/Default.fnt");
            gui.textHAlign("center");
            gui.textVAlign("bottom");

            for (GuiElement el : snapshot) {
                gui.textSize(el.sizePixels);
                gui.textColor(el.color);
                gui.text(el.word, el.x, el.y, (event, arg) -> {
                    if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                        markClicked(el.handle);
                    }
                    return true;
                });
            }

            gui.pop();
        }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drains every element currently flagged clickRegistered == false,
     * resets each back to true (consumed), and returns their tags so the
     * caller can dispatch tick(tpf, tag) for each. Called once per frame
     * from Main, separately from draw().
     */
    public List<String> drainClickedTags() {
        lock.lock();
        try {
        List<String> clicked = new ArrayList<>();
        for (GuiElement el : drawOrder) {
            if (!el.clickRegistered) {
                clicked.add(el.tag);
                el.clickRegistered = true;
            }
        }
        return clicked;
        } finally {
            lock.unlock();
        }
    }

    public void setDraw(boolean canD) {
        lock.lock();
        try {
            this.canDraw = canD;
        } finally {
            lock.unlock();
        }
    }

    @HostAccess.Export
    public boolean getDraw() {
        lock.lock();
        try {
            return canDraw;
        } finally {
            lock.unlock();
        }
    }

    private void markClicked(long handle) {
        lock.lock();
        try {
            GuiElement element = byHandle.get(handle);
            if (element != null) element.clickRegistered = false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Exposes the current gui path (e.g. "home", "play", "home/modview/LFTsidebar")
     * to JS, so an always-on pack can suppress its own drawing in specific
     * states (e.g. hide itself while its own modview detail page is open).
     * Read-only from JS's perspective — Gui.getGuiState() only.
     */
    @HostAccess.Export
    public String getGuiState() {
        return com.mtsharpgrain.gui.GameState.guiState;
    }
    
}
