package com.mtsharpgrain.js.mainthread;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists the set of "saved" mod pack names to a flat text file, one name
 * per line — worlds/<world>/saved_mods.txt (sibling to the mod/ and chunks/
 * folders, same convention as ChunkFileHelper).
 *
 * This only stores names. If a saved pack is later deleted/renamed on disk,
 * its name stays in the file (harmless — callers should cross-check against
 * ModPackManager.getPackNames() before treating it as navigable).
 */
public class SavedModsStore {

    private final Path file;
    private final Set<String> saved = new LinkedHashSet<>(); // insertion order for stable display

    public SavedModsStore(Path worldFolder) {
        this.file = worldFolder.resolve("saved_mods.txt");
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) saved.add(trimmed);
            }
        } catch (IOException e) {
            System.err.println("[SavedModsStore] failed to load " + file + ": " + e.getMessage());
        }
    }

    private void persist() {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temp, saved);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[SavedModsStore] failed to save " + file + ": " + e.getMessage());
        }
    }

    public boolean isSaved(String packName) {
        return saved.contains(packName);
    }

    public void setSaved(String packName, boolean isSaved) {
        boolean changed = isSaved ? saved.add(packName) : saved.remove(packName);
        if (changed) persist();
    }

    public List<String> getSavedNames() {
        return new ArrayList<>(saved);
    }
}
