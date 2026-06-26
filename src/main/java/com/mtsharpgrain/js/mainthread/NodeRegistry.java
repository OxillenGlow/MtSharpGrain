package com.mtsharpgrain.js.mainthread;

import com.jme3.scene.Spatial;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the real jME {@link Spatial} objects. JS never gets a direct reference -
 * it only ever sees the {@code long} handle returned by {@link #register(Spatial)}.
 * This is the pointer system: Java resolves handle -> object, JS just passes
 * the long around.
 *
 * Not thread-safe across Graal Contexts running on different threads - all
 * calls are expected to happen on the render/main thread, same as the
 * Context itself.
 */
public class NodeRegistry {

    private final Map<Long, Spatial> handles = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    /** Registers a Spatial and returns the handle JS will use to refer to it. */
    public long register(Spatial spatial) {
        long id = nextId.getAndIncrement();
        handles.put(id, spatial);
        return id;
    }

    /** Resolves a handle back to its real Spatial. Throws if the handle is unknown/freed. */
    public Spatial get(long handle) {
        Spatial spatial = handles.get(handle);
        if (spatial == null) {
            throw new IllegalArgumentException("Invalid or freed node handle: " + handle);
        }
        return spatial;
    }

    /** Returns true if the handle currently resolves to a live Spatial. */
    public boolean isValid(long handle) {
        return handles.containsKey(handle);
    }

    /** Removes a handle from the registry. Does not detach it from its parent - callers should do that first. */
    public void free(long handle) {
        handles.remove(handle);
    }

    /** Registers a pre-existing Spatial (e.g. the world root) under a specific fixed handle. */
    public void registerFixed(long handle, Spatial spatial) {
        handles.put(handle, spatial);
    }
}
