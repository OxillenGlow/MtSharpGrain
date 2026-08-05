package com.mtsharpgrain.js;

import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;
import org.graalvm.polyglot.HostAccess;
import com.mtsharpgrain.js.mainthread.EngineAccess;

/**
 * Exposes block operations to JS. Validated edits go through WorldAccess;
 * forceSet remains an explicit direct render-invalidation path.
 */
public class BlockApi {

    private final WorldAccess worldAccess;
    private final RenderManager renderManager;
    private final EngineAccess engine;

    public BlockApi(WorldAccess worldAccess, RenderManager renderManager, EngineAccess engine) {
        this.worldAccess = worldAccess;
        this.renderManager = renderManager;
        this.engine = engine;
    }

    @HostAccess.Export
    public void placeBlock(int x, int y, int z, int blockId) {
        try {
            worldAccess.requestBlockChange(x, y, z, blockId);
        } catch (Throwable t) {
            System.err.println("[BlockApi] placeBlock scheduling failed: " + t.getMessage());
        }
    }

    @HostAccess.Export
    public void destroyBlock(int x, int y, int z) {
        try {
            worldAccess.requestRemoveBlock(x, y, z);
        } catch (Throwable t) {
            System.err.println("[BlockApi] placeBlock scheduling failed: " + t.getMessage());
        }
    }

    // This is raw and needs to be done in render thead. 
    @HostAccess.Export
    public void forceSetBlock(int x, int y, int z, int blockId) {
        engine.post(() -> {
            worldAccess.forceSetBlockAt(x, y, z, blockId);
            renderManager.onBlockChanged(x, y, z);
        });
    }

    @HostAccess.Export
    public int getBlock(int x, int y, int z) {
        return engine.call(() -> worldAccess.getBlockAt(x, y, z));
    }
}
