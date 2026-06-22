package com.mtsharpgrain.js;

import com.mtsharpgrain.BufferedChunk;
import org.graalvm.polyglot.HostAccess;

/** Bound to the JS `Chunk` global only while a chunk is being built. */
public class ChunkWriterApi {

    private final BufferedChunk chunk;

    public ChunkWriterApi(BufferedChunk chunk) {
        this.chunk = chunk;
    }

    /** localX/Y/Z are 0-15, relative to this chunk. */
    @HostAccess.Export
    public void set(int localX, int localY, int localZ, int blockId) {
        chunk.set(localX, localY, localZ, blockId);
    }

    @HostAccess.Export
    public int get(int localX, int localY, int localZ) {
        return chunk.get(localX, localY, localZ);
    }
}
