package com.mtsharpgrain.js;

import com.mtsharpgrain.ChunkBinaryIO;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Bound to JS for the lifetime of the engine — reads chunk template files (sky/ground swaps). */
public class ChunkArrayApi {

    private final Path templatesRoot; // e.g. "worlds" — sibling of "worlds/my_world"
    private final Map<String, List<Path>> fileListCache = new HashMap<>();

    public ChunkArrayApi(Path templatesRoot) {
        this.templatesRoot = templatesRoot;
    }

    /** path like "storageAir/blob_03.chunk" — returns a flat int[4096], or null on failure. */
    @HostAccess.Export
    public int[] getChunkAsArray(String relativePath) {
        try {
            return ChunkBinaryIO.readFlat(templatesRoot.resolve(relativePath));
        } catch (IOException e) {
            System.err.println("Failed to load chunk template: " + relativePath);
            return null;
        }
    }

    /**
     * Deterministically picks a file from templatesRoot/folderName using roll01 (0..1, supplied by JS hash2).
     * Returns a relative path usable with getChunkAsArray, or null if the folder has no .chunk files.
     */
    @HostAccess.Export
    public String pickFile(String folderName, double roll01) {
        List<Path> files = fileListCache.computeIfAbsent(folderName, f -> listChunkFiles(templatesRoot.resolve(f)));
        if (files.isEmpty()) return null;
        int idx = (int) (roll01 * files.size());
        if (idx >= files.size()) idx = files.size() - 1; // guard roll01 == 1.0
        return folderName + "/" + files.get(idx).getFileName();
    }

    private List<Path> listChunkFiles(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".chunk"))
                    .sorted() // stable order so the same roll01 always maps to the same file
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }
}
