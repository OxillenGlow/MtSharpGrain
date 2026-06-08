package com.mtsharpgrain;

import java.io.*;
import java.nio.file.*;

public final class ChunkFileHelper {

    private final Path folder;
    // 16 * 16 * 16 blocks * 4 bytes per int = 16384 bytes
    private static final long EXPECTED_SIZE = BufferedChunk.SIZE * BufferedChunk.SIZE * BufferedChunk.SIZE * 4L;

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

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempPath)))) {
            int[][][] b = chunk.getRaw();
            for (int x = 0; x < BufferedChunk.SIZE; x++)
                for (int y = 0; y < BufferedChunk.SIZE; y++)
                    for (int z = 0; z < BufferedChunk.SIZE; z++)
                        dos.writeInt(b[x][y][z]);
            
            dos.flush();
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

        // --- FIX: Check for corrupted/empty files ---
        try {
            if (Files.size(p) < EXPECTED_SIZE) {
                System.err.println("Deleting corrupted chunk file (too small): " + p);
                Files.delete(p);
                return null;
            }
        } catch (IOException e) {
            return null;
        }

        BufferedChunk chunk = new BufferedChunk();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            int[][][] b = chunk.getRaw();
            for (int x = 0; x < BufferedChunk.SIZE; x++)
                for (int y = 0; y < BufferedChunk.SIZE; y++)
                    for (int z = 0; z < BufferedChunk.SIZE; z++)
                        b[x][y][z] = dis.readInt();
        } catch (EOFException e) {
            System.err.println("EOF reached unexpectedly in " + p);
            try { Files.delete(p); } catch (IOException ignore) {}
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return chunk;
    }
}
