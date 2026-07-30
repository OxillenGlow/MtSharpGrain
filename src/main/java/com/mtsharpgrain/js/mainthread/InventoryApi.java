package com.mtsharpgrain.js.mainthread;

import com.mtsharpgrain.gui.Inventory;
import org.graalvm.polyglot.HostAccess;

/** Marshals JS inventory calls to the render thread and the shared inventory. */
public final class InventoryApi {
    private final Inventory inventory;
    private final EngineAccess engine;

    public InventoryApi(Inventory inventory, EngineAccess engine) {
        this.inventory = inventory;
        this.engine = engine;
    }

    @HostAccess.Export
    public boolean addItem(int blockId, int amount) {
        return engine.call(() -> inventory.addItem(blockId, amount));
    }

    @HostAccess.Export
    public boolean removeItem(int blockId, int amount) {
        return engine.call(() -> inventory.removeItem(blockId, amount));
    }

    @HostAccess.Export
    public int getAmount(int blockId) {
        return engine.call(() -> inventory.getAmount(blockId));
    }
}