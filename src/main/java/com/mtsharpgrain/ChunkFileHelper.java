package com.mtsharpgrain;

import com.tools.ChunkZipper;

import java.io.IOException;
import java.nio.file.*;

public final class ChunkFileHelper {

    private final Path folder;

    public ChunkFileHelper(String worldFolder) {
        folder = Paths.get(worldFolder, "chunks");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path chunkPath(int x, int y, int z) {
        return folder.resolve(x + "_" + y + "_" + z + ".chunk");
    }

    public void saveChunk(ChunkPos pos, BufferedChunk chunk) {
        // Use a temporary file to prevent 0-byte corruption if the game crashes during save
        Path finalPath = chunkPath(pos.x, pos.y, pos.z);
        Path tempPath = finalPath.resolveSibling(finalPath.getFileName() + ".tmp");

        byte[] compressed = ChunkZipper.Compress(chunk.getRaw());
        if (compressed == null) {
            System.err.println("Failed to compress chunk at " + pos + " - not saving");
            return;
        }

        try {
            Files.write(tempPath, compressed);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public BufferedChunk loadChunk(ChunkPos pos) {
        Path p = chunkPath(pos.x, pos.y, pos.z);

        if (!Files.exists(p)) return null;

        byte[] compressed;
        try {
            compressed = Files.readAllBytes(p);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // --- FIX: Check for corrupted/empty files ---
        // Compressed size varies with content, so we can no longer sanity-check
        // against a fixed expected byte count (was EXPECTED_SIZE for raw ints).
        // An empty file is still an unambiguous corruption signal though.
        if (compressed.length == 0) {
            System.err.println("Deleting corrupted chunk file (empty): " + p);
            try { Files.delete(p); } catch (IOException ignore) {}
            return null;
        }

        int[][][] raw = ChunkZipper.Decompress(compressed);
        if (raw == null) {
            // ChunkZipper.Decompress prints its own stack trace and returns null on failure
            System.err.println("Deleting corrupted chunk file (failed to inflate): " + p);
            try { Files.delete(p); } catch (IOException ignore) {}
            return null;
        }

        BufferedChunk chunk = new BufferedChunk();
        int[][][] dest = chunk.getRaw();
        for (int x = 0; x < BufferedChunk.SIZE; x++)
            for (int y = 0; y < BufferedChunk.SIZE; y++)
                System.arraycopy(raw[x][y], 0, dest[x][y], 0, BufferedChunk.SIZE);

        return chunk;
    }
}
