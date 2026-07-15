package com.mtsharpgrain.js.mainthread;

import com.jme.igui.IGui;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Owns one {@link JSModifier} (own GraalVM Context) per mod-pack subfolder
 * under a mods root, e.g.:
 *
 * <pre>
 * worlds/my_world/mod/
 *   loose.js            <- NOT loaded (no longer supported at this level)
 *   packA/
 *     init.js
 *     sub/more.js        <- still loaded, packA's context, recursive+alphabetical
 *   packB/
 *     test.js
 * </pre>
 *
 * Each pack is fully isolated: separate Context, separate Scene/Engine/Gui/
 * Player globals, separate BlockChangeRegistry. Packs are loaded in
 * alphabetical folder-name order for determinism, matching the previous
 * single-context alphabetical file order.
 *
 * Main.java should only ever touch this class, not JSModifier directly.
 */
public class ModPackManager {

    private final Map<String, JSModifier> packs = new LinkedHashMap<>();

    /**
     * Discovers immediate subdirectories of {@code modRoot}, gives each its
     * own initialized {@link JSModifier}, and recursively loads every .js
     * file under that subdirectory (alphabetically) into it. Files directly
     * inside {@code modRoot} (not in a subfolder) are intentionally ignored.
     */
    public void loadAll(Path modRoot, AssetManager assetManager, Node rootNode,
                         WorldAccess worldAccess, RenderManager renderManager, Camera cam) throws IOException {
        if (!Files.exists(modRoot)) {
            Logger.getLogger(ModPackManager.class.getName()).log(Level.WARNING,
                    "Mod folder not found, skipping: " + modRoot.toAbsolutePath());
            return;
        }

        List<Path> packDirs;
        try (var stream = Files.list(modRoot)) {
            packDirs = stream.filter(Files::isDirectory)
                    .sorted() // deterministic pack load order across platforms
                    .collect(Collectors.toList());
        }

        for (Path dir : packDirs) {
            String packName = dir.getFileName().toString();

            JSModifier modifier = new JSModifier();
            modifier.init(assetManager, rootNode, worldAccess, renderManager, cam);

            try (var walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".js"))
                        .sorted() // deterministic file order within the pack
                        .forEach(p -> {
                            try {
                                modifier.runJs(p.toFile());
                            } catch (IOException ex) {
                                Logger.getLogger(ModPackManager.class.getName()).log(Level.SEVERE,
                                        "Failed to load mod script '" + p + "' in pack '" + packName + "'", ex);
                            }
                        });
            }

            packs.put(packName, modifier);
        }
    }

    /** Returns the pack's JSModifier, or null if no pack with that name was loaded. */
    public JSModifier getMod(String packName) {
        return packs.get(packName);
    }

    public Set<String> getPackNames() {
        return packs.keySet();
    }

    // ── Aggregate lifecycle calls — drive every pack from one place ────────

    public void tick(float tpf, String guiTag) {
        for (JSModifier m : packs.values()) m.tick(tpf, guiTag);
    }

    public void tickAll(float tpf) {
        for (JSModifier m : packs.values()) m.tickAll(tpf);
    }

    public void draw(IGui gui) {
        for (JSModifier m : packs.values()) m.draw(gui);
    }

    public void processGuiClicks(float tpf) {
        for (JSModifier m : packs.values()) m.processGuiClicks(tpf);
    }

    /**
     * Aggregate block-change validation across every pack. DESIGN CHOICE
     * (flagging, not deciding for you): each pack has its own isolated
     * BlockChangeRegistry now, since each has its own Context. This runs
     * every pack's validators and fails closed if ANY pack rejects the
     * change — matching the previous fail-closed single-registry behavior,
     * just extended across packs. If you'd rather scope validation to a
     * specific pack, or short-circuit differently, this is the method to
     * change.
     */
    public void notifyBlockSet(int worldX, int worldY, int worldZ, int blockId) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            e.getValue().notifyBlockSet(worldX, worldY, worldZ, blockId);
        }
    }
}
