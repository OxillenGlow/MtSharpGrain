package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Simple per-pack XML save-data API, exposed to JS as the {@code Data} global:
 *   Data.save(data, location)
 *   Data.get(location)
 *
 * Each pack gets its own storage folder so mods can't read/clobber each
 * other's save data:
 *   worlds/<world>/mod/<packName>/data/<location>.xml
 *
 * Values are always strings from JS's perspective — for structured data,
 * mods should JSON.stringify() before save() and JSON.parse() after get().
 * Uses java.util.Properties' storeToXML/loadFromXML under a single "value"
 * key, and writes via a temp file + atomic move (same pattern as
 * ChunkFileHelper.saveChunk) so a crash mid-write can't corrupt the file.
 */
public class DataApi {

    private final Path dataRoot; // worlds/<world>/mod/<packName>/data

    public DataApi(Path packDir) {
        this.dataRoot = packDir.resolve("data");
    }

    @HostAccess.Export
    public void save(String data, String location) {
        String safe = sanitize(location);
        Path file = dataRoot.resolve(safe + ".xml");
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(dataRoot);
            Properties props = new Properties();
            props.setProperty("value", data == null ? "" : data);
            try (OutputStream os = Files.newOutputStream(temp)) {
                props.storeToXML(os, "MtSharpGrain save data: " + safe);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[DataApi] failed to save '" + location + "': " + e.getMessage());
        }
    }

    @HostAccess.Export
    public String get(String location) {
        Path file = dataRoot.resolve(sanitize(location) + ".xml");
        if (!Files.exists(file)) return null;
        try (InputStream is = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.loadFromXML(is);
            return props.getProperty("value");
        } catch (IOException e) {
            System.err.println("[DataApi] failed to load '" + location + "': " + e.getMessage());
            return null;
        }
    }

    /** Strips anything that isn't alnum/_/- so "location" can't path-traverse out of dataRoot. */
    private static String sanitize(String location) {
        if (location == null || location.isEmpty()) return "default";
        return location.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
