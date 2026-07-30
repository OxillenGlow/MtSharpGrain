package com.mtsharpgrain.js;

import com.mtsharpgrain.WorldAccess;
import org.graalvm.polyglot.HostAccess;

/**
 * Exposes block operations to JS. Delegates all changes to WorldAccess only.
 * WorldAccess is responsible for notifying the RenderManager when a change
 * fully completes.
 */
public class BlockApi {

    private final WorldAccess worldAccess;

    public BlockApi(WorldAccess worldAccess) {
        this.worldAccess = worldAccess;
    }

    @HostAccess.Export
    public void placeBlock(int x, int y, int z, int blockId) {
        worldAccess.setBlockAt(x, y, z, blockId);
    }

    @HostAccess.Export
    public void destroyBlock(int x, int y, int z) {
        worldAccess.removeBlockAt(x, y, z);
    }

    @HostAccess.Export
    public void forceSetBlock(int x, int y, int z, int blockId) {
        worldAccess.forceSetBlockAt(x, y, z, blockId);
    }

    @HostAccess.Export
    public int getBlock(int x, int y, int z) {
        return worldAccess.getBlockAt(x, y, z);
    }
}
