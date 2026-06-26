package com.mtsharpgrain.js.mainthread;

/**
 * Bridge between {@link SceneApi#getBlockId(int, int, int)} and your actual
 * world/chunk storage. I don't have visibility into your voxel/world system,
 * so this is intentionally just an interface + a no-op stub.
 *
 * TODO: write a real implementation of this against your chunk manager
 * (coordinate -> chunk lookup, local-coordinate translation, etc.) and pass
 * it into {@link SceneApi}'s constructor instead of {@link Stub}.
 */
public interface WorldAccessor {

    /** Returned by the stub for any coordinate - treat as "unknown/air". */
    int UNKNOWN = -1;

    /**
     * @param x world block x
     * @param y world block y
     * @param z world block z
     * @return the block id at that coordinate
     */
    int getBlockId(int x, int y, int z);

    /**
     * Always-returns-UNKNOWN stub so getBlockId compiles and runs safely
     * before the real world-lookup is wired in.
     */
    class Stub implements WorldAccessor {
        @Override
        public int getBlockId(int x, int y, int z) {
            // TODO: replace with a real lookup, something like:
            // Chunk chunk = worldManager.getChunkContaining(x, y, z);
            // return chunk == null ? UNKNOWN : chunk.getBlockId(x & 15, y, z & 15);
            return UNKNOWN;
        }
    }
}
