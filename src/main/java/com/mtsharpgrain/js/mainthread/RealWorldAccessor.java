package com.mtsharpgrain.js.mainthread;

import com.mtsharpgrain.WorldAccess;

/**
 * Real implementation of {@link WorldAccessor}, wired to your actual
 * voxel/world storage via {@link WorldAccess#getBlockAt(int, int, int)}.
 *
 * This replaces the old "World.java" file, which didn't compile - it was
 * missing a package declaration and referenced a misspelled/miscased type
 * (com.mtsharpgrain.Worldaccess instead of com.mtsharpgrain.WorldAccess).
 * Delete World.java once this file is in place.
 */
public class RealWorldAccessor implements WorldAccessor {

    private final WorldAccess worldAccess;

    public RealWorldAccessor(WorldAccess worldAccess) {
        this.worldAccess = worldAccess;
    }

    @Override
    public int getBlockId(int x, int y, int z) {
        return worldAccess.getBlockAt(x, y, z);
    }
}
