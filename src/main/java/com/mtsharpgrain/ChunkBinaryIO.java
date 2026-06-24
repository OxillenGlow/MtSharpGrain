package com.mtsharpgrain;

import java.io.*;
import java.nio.file.*;

public final class ChunkBinaryIO {
    public static int[] readFlat(Path path) throws IOException {
        int total = BufferedChunk.SIZE * BufferedChunk.SIZE * BufferedChunk.SIZE;
        int[] flat = new int[total];
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            for (int i = 0; i < total; i++) flat[i] = dis.readInt();
        }
        return flat;
    }
    private ChunkBinaryIO() {}
}
