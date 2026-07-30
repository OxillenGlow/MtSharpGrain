package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores JS callbacks for left- and right-clicks on non-chunk spatials and
 * dispatches them by name when {@link #notifyLeftClick} / {@link #notifyRightClick}
 * is called from the game side.
 *
 * <p>Wired into the JS {@code Engine} global by {@link JsApiBootstrap}:
 * <pre>
 *   Engine.onSpatialLeftClick(function(name) { ... });
 *   Engine.onSpatialRightClick(function(name) { ... });
 * </pre>
 *
 * The {@code name} passed to the callback is exactly the name the spatial was
 * given at creation time (e.g. {@code Scene.createCube("myBox", ...)}) —
 * no userdata is involved.
 */
public class SpatialClickRegistry {

    private final List<Value> leftHandlers  = new ArrayList<>();
    private final List<Value> rightHandlers = new ArrayList<>();

    // ── Registration (called from JS) ────────────────────────────────────

    @HostAccess.Export
    public void onLeftClick(Value fn) {
        if (fn != null && fn.canExecute()) {
            leftHandlers.add(fn);
        }
    }

    @HostAccess.Export
    public void onRightClick(Value fn) {
        if (fn != null && fn.canExecute()) {
            rightHandlers.add(fn);
        }
    }

    // ── Notification (called from Java) ──────────────────────────────────

    /**
     * Fires every registered left-click handler with the spatial's name.
     * Called on the owning mod virtual thread after the render thread submits
     * a non-chunk spatial click.
     */
    public void notifyLeftClick(String spatialName) {
        for (Value fn : leftHandlers) {
            try {
                fn.execute(spatialName);
            } catch (Exception e) {
                System.err.println("[SpatialClick] left-click handler threw for '" + spatialName + "': " + e.getMessage());
            }
        }
    }

    /**
     * Fires every registered right-click handler with the spatial's name.
     * Called on the owning mod virtual thread after the render thread submits
     * a non-chunk spatial click.
     */
    public void notifyRightClick(String spatialName) {
        for (Value fn : rightHandlers) {
            try {
                fn.execute(spatialName);
            } catch (Exception e) {
                System.err.println("[SpatialClick] right-click handler threw for '" + spatialName + "': " + e.getMessage());
            }
        }
    }
}
