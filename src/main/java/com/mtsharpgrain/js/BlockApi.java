package com.mtsharpgrain.js;

import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;
import org.graalvm.polyglot.HostAccess;
import com.mtsharpgrain.js.mainthread.EngineAccess;

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
        await(worldAccess.requestBlockChange(x, y, z, blockId));
    }

    @HostAccess.Export
    public void destroyBlock(int x, int y, int z) {
        await(worldAccess.requestRemoveBlock(x, y, z));
    }

    @HostAccess.Export
    public void forceSetBlock(int x, int y, int z, int blockId) {
        engine.run(() -> {
            worldAccess.forceSetBlockAt(x, y, z, blockId);
            renderManager.onBlockChanged(x, y, z);
        });
    }

    private void await(java.util.concurrent.CompletableFuture<Boolean> result) {
        try {
            if (!result.get()) {
                throw new IllegalStateException("Block change was rejected");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for block change", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Block change failed", e.getCause());
        }
    }

    @HostAccess.Export
    public int getBlock(int x, int y, int z) {
        return engine.call(() -> worldAccess.getBlockAt(x, y, z));
    }
}
