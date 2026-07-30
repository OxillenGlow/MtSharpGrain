package com.mtsharpgrain.js;

import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;
import org.graalvm.polyglot.HostAccess;

public class BlockApi {

    private final WorldAccess worldAccess;
    private final RenderManager renderManager;

    public BlockApi(WorldAccess worldAccess, RenderManager renderManager) {
        this.worldAccess = worldAccess;
        this.renderManager = renderManager;
    }

    @HostAccess.Export
    public void placeBlock(int x, int y, int z, int blockId) {
        worldAccess.setBlockAt(x, y, z, blockId);
        renderManager.onBlockChanged(x, y, z);
    }

    @HostAccess.Export
    public void destroyBlock(int x, int y, int z) {
        worldAccess.removeBlockAt(x, y, z);
        renderManager.onBlockChanged(x, y, z);
    }

    @HostAccess.Export
    public void forceSetBlock(int x, int y, int z, int blockId) {
        worldAccess.forceSetBlockAt(x, y, z, blockId);
        renderManager.onBlockChanged(x, y, z);
    }

    @HostAccess.Export
    public int getBlock(int x, int y, int z) {
        return worldAccess.getBlockAt(x, y, z);
    }
}
