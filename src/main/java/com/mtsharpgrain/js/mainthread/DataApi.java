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
 * Per-pack save-data API, exposed to JS as the {@code Data} global:
 *   Data.save(data, location)   — sets a key in the in-memory buffer
 *   Data.get(location)          — reads a key from the in-memory buffer
 *
 * One file per pack now, not one file per key:
 *   worlds/<world>/mod/<packName>/data.xml
 *
 * Loaded once at construction into an in-memory Properties buffer. Data.save()
 * only mutates the buffer — it does NOT touch disk. The buffer is written out
 * only when save() (no-arg, Java-only) is called, which is wired up to fire
 * once at shutdown via JSModifier.onClose() -> ModPackManager.onClose() ->
 * Main.destroy(). This avoids a disk write on every single key set, at the
 * cost of losing unsaved changes on a crash (same trade-off ChunkFileHelper's
 * in-memory-then-flush pattern makes elsewhere, just applied here too).
 */
public class DataApi {

    private final Path dataFile; // worlds/<world>/mod/<packName>/data.xml
    private final Properties buffer = new Properties();

    public DataApi(Path packDir) {
        this.dataFile = packDir.resolve("data.xml");
        load();
    }

    private void load() {
        if (!Files.exists(dataFile)) return;
        try (InputStream is = Files.newInputStream(dataFile)) {
            buffer.loadFromXML(is);
        } catch (IOException e) {
            System.err.println("[DataApi] failed to load " + dataFile + ": " + e.getMessage());
        }
    }

    /** Sets a key in the in-memory buffer. Does NOT write to disk — call save() for that. */
    @HostAccess.Export
    public void save(String data, String location) {
        buffer.setProperty(sanitize(location), data == null ? "" : data);
    }

    @HostAccess.Export
    public String get(String location) {
        return buffer.getProperty(sanitize(location));
    }

    /**
     * Writes the entire in-memory buffer to data.xml. NOT exported to JS —
     * this is a Java-only lifecycle call, driven by JSModifier.onClose().
     * Temp-file + atomic-move, same crash-safety pattern as ChunkFileHelper.
     */
    public void save() {
        Path temp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(dataFile.getParent());
            try (OutputStream os = Files.newOutputStream(temp)) {
                buffer.storeToXML(os, "MtSharpGrain save data");
            }
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[DataApi] failed to save " + dataFile + ": " + e.getMessage());
        }
    }

    /** Strips anything that isn't alnum/_/- so "location" can't path-traverse or collide with a bad key. */
    private static String sanitize(String location) {
        if (location == null || location.isEmpty()) return "default";
        return location.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
