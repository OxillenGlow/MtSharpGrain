package com.mtsharpgrain;

import com.tools.ChunkZipper;

import java.io.IOException;
import java.nio.file.*;

public final class ChunkBinaryIO {

    public static int[] readFlat(Path path) throws IOException {
        byte[] compressed = Files.readAllBytes(path);

        int[][][] raw = ChunkZipper.Decompress(compressed);
        if (raw == null) {
            throw new IOException("Failed to inflate chunk template: " + path);
        }

        int total = BufferedChunk.SIZE * BufferedChunk.SIZE * BufferedChunk.SIZE;
        int[] flat = new int[total];
        int i = 0;
        for (int x = 0; x < BufferedChunk.SIZE; x++)
            for (int y = 0; y < BufferedChunk.SIZE; y++)
                for (int z = 0; z < BufferedChunk.SIZE; z++)
                    flat[i++] = raw[x][y][z];

        return flat;
    }

    private ChunkBinaryIO() {}
}
