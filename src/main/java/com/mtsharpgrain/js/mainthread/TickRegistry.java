package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets JS register per-tick callbacks via the {@code Engine} global. Each
 * callback carries a tag (e.g. the tag of a gui element or feature it's
 * driving) which gets passed back into the JS function on every call, so a
 * single generic handler can tell which thing it's ticking for:
 *
 * <pre>{@code
 * Engine.onTick(function(tpf, tag) {
 *     if (tag === "myFloatingLabel") { ... }
 * }, "myFloatingLabel");
 * }</pre>
 *
 * IMPORTANT: {@link #tick(float)} and {@link #tickTag(float, String)} must
 * always be called from the virtual thread that owns the Graal Context these
 * callbacks belong to. ModBridge ensures the render thread never calls them
 * directly.
 */
public class TickRegistry {

    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final List<TickCallback> callbacks = new CopyOnWriteArrayList<>();

    @HostAccess.Export
    public void onTick(Value fn, String tag) {
        if (!fn.canExecute()) {
            throw new IllegalArgumentException("onTick expects a function as the first argument");
        }
        callbacks.add(new TickCallback(fn, tag));
    }

    /** Convenience overload for callbacks with no associated tag. */
    @HostAccess.Export
    public void onTick(Value fn) {
        onTick(fn, "");
    }

    /** Calls every registered callback, regardless of tag. */
    public void tick(float tpf) {
        for (TickCallback cb : callbacks) {
            runCallback(cb, tpf);
        }
    }

    /**
     * Calls only the callbacks registered under the given tag - lets you
     * drive a single gui-tagged script on demand (e.g. "run this widget's
     * logic now") instead of ticking everything every frame.
     */
    public void tickTag(float tpf, String tag) {
        String safeTag = tag == null ? "" : tag;
        for (TickCallback cb : callbacks) {
            if (cb.tag.equals(safeTag)) {
                runCallback(cb, tpf);
            }
        }
    }

    private void runCallback(TickCallback cb, float tpf) {
        try {
            cb.fn.execute(tpf, cb.tag);
            cb.failureCount = 0;
        } catch (PolyglotException e) {
            cb.failureCount++;
            // TODO: route to your real logger instead of stderr
            System.err.println("[TickRegistry] callback for tag '" + cb.tag + "' threw: " + e.getMessage());
            if (cb.failureCount >= MAX_CONSECUTIVE_FAILURES) {
                System.err.println("[TickRegistry] disabling callback for tag '" + cb.tag
                        + "' after " + cb.failureCount + " consecutive failures");
                callbacks.remove(cb);
            }
        }
    }

    /** Removes all callbacks associated with the given tag - e.g. when a mod/gui element unloads. */
    public void removeByTag(String tag) {
        callbacks.removeIf(cb -> cb.tag.equals(tag));
    }

    private static final class TickCallback {
        final Value fn;
        final String tag;
        int failureCount = 0;

        TickCallback(Value fn, String tag) {
            this.fn = fn;
            this.tag = tag == null ? "" : tag;
        }
    }
}
