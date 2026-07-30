package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets JS register block-change validators via {@code Engine.onBlockChange}:
 *
 * <pre>{@code
 * Engine.onBlockChange(function(x, y, z, blockId) {
 *     return blockId !== 9; // reject metal block placement
 * });
 * }</pre>
 *
 * Distinct from {@link TickRegistry}: not tagged/dispatched by tick group.
 * Each validator runs on its owning pack's virtual thread and its return value
 * decides whether the edit is allowed. WorldAccess commits the edit later on
 * the render thread after all validator futures complete.
 *
 * This registry must only be called by the virtual thread that owns its
 * GraalJS Context. ModPackManager enforces that boundary through ModBridge.
 */
public class BlockChangeRegistry {

    private final List<Value> validators = new CopyOnWriteArrayList<>();

    @HostAccess.Export
    public void onBlockChange(Value fn) {
        if (!fn.canExecute()) {
            throw new IllegalArgumentException("onBlockChange expects a function");
        }
        validators.add(fn);
    }

    /**
     * Fail-closed: a validator that throws rejects the change rather than
     * being silently ignored. Unlike TickRegistry's runCallback, this isn't
     * something you want to fail open on - a busted validator should block
     * edits, not let everything through unchecked.
     */
    public boolean checkBlockChange(int x, int y, int z, int blockId) {
        for (Value fn : validators) {
            try {
                if (!fn.execute(x, y, z, blockId).asBoolean()) {
                    return false;
                }
            } catch (PolyglotException e) {
                System.err.println("[BlockChangeRegistry] validator threw, rejecting change: " + e.getMessage());
                return false;
            }
        }
        return true;
    }
}
