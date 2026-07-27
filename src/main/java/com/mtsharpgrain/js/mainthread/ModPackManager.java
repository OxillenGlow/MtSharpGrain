package com.mtsharpgrain.js.mainthread;

import com.jme.igui.IGui;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;
import com.mtsharpgrain.gui.Inventory;
import java.util.HashSet;
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

    // ── Utility "always-on" packs ───────────────────────────────────────────
    // A pack whose folder name starts with one of these is exempt from the
    // normal draw gating (setOnlyDrawing/disableAllDrawing) — it's meant to be
    // a persistent HUD element (left/right/bottom dock), so it stays visible
    // across every gui path. Positioning itself (which x/y to draw at) is still
    // entirely up to the mod's own JS via Gui.guiWord — this only controls
    // *whether* GuiApi.draw() is allowed to run for that pack each frame.
    private static final String[] ALWAYS_ON_PREFIXES = {"LFT", "RHT", "BTM", "UTIL", "MODE"};

    private static boolean isAlwaysOn(String packName) {
        for (String prefix : ALWAYS_ON_PREFIXES) {
            if (packName.startsWith(prefix)) return true;
        }
        return false;
    }

    // Render-thread only, same as `packs` — not concurrent-safe by design.
    private final Set<String> disabledPacks = new HashSet<>();
    private final Map<String, JSModifier> packs = new LinkedHashMap<>();
    private SavedModsStore savedModsStore;
    private Path modRoot; // cached so reloadPack() can find a pack's folder: modRoot/<packName>
    // Cached dependencies for reloading
    private AssetManager cachedAssetManager;
    private Node cachedRootNode;
    private WorldAccess cachedWorldAccess;
    private com.mtsharpgrain.RenderManager cachedRenderManager;
    private com.jme3.renderer.Camera cachedCam;
    private Inventory cachedInventory;
    
    /**
     * Discovers immediate subdirectories of {@code modRoot}, gives each its
     * own initialized {@link JSModifier}, and recursively loads every .js
     * file under that subdirectory (alphabetically) into it. Files directly
     * inside {@code modRoot} (not in a subfolder) are intentionally ignored.
     */
    public void loadAll(Path modRoot, AssetManager assetManager, Node rootNode, WorldAccess worldAccess, RenderManager renderManager, Camera cam, Inventory inventory) throws IOException {
        if (!Files.exists(modRoot)) {
            Logger.getLogger(ModPackManager.class.getName()).log(Level.WARNING,
                    "Mod folder not found, skipping: " + modRoot.toAbsolutePath());
            return;
        }

        // Cached so reloadPack() can rebuild a pack later without new arguments
        this.modRoot = modRoot;
        this.cachedAssetManager = assetManager;
        this.cachedRootNode = rootNode;
        this.cachedWorldAccess = worldAccess;
        this.cachedRenderManager = renderManager;
        this.cachedCam = cam;
        this.cachedInventory = inventory;

        Path worldFolder = modRoot.getParent() != null ? modRoot.getParent() : modRoot;
        savedModsStore = new SavedModsStore(worldFolder);

        List<Path> packDirs;
        try (var stream = Files.list(modRoot)) {
            packDirs = stream.filter(Files::isDirectory)
                    .sorted() // deterministic pack load order across platforms
                    .collect(Collectors.toList());
        }

        for (Path dir : packDirs) {
            String packName = dir.getFileName().toString();

            JSModifier modifier = new JSModifier();
            modifier.init(assetManager, rootNode, worldAccess, renderManager, cam, dir, packName, this, inventory);

            try (var walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".js"))
                        .sorted() // deterministic file order within the pack
                        .forEach(p -> {
                            try {
                                modifier.runJs(p.toFile());
                            } catch (Exception ex) {
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

    /** Sorted pack names, for stable table rendering. */
    public List<String> getSortedPackNames() {
        return packs.keySet().stream().sorted().collect(Collectors.toList());
    }

   public boolean isEnabled(String packName) {
        return packs.containsKey(packName) && !disabledPacks.contains(packName);
    }

    /** Disabling a pack stops its tick/draw/click/validator calls entirely — it goes fully inert. */
    public void setEnabled(String packName, boolean enabled) {
        if (!packs.containsKey(packName)) return;
        if (enabled) disabledPacks.remove(packName);
        else disabledPacks.add(packName);
    }

    // ── Aggregate lifecycle calls — drive every pack from one place ────────

    public void tick(float tpf, String guiTag) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().tick(tpf, guiTag);
        }
    }

    public void tickAll(float tpf) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().tickAll(tpf);
        }
    }

    public void draw(IGui gui) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().draw(gui);
        }
    }

    public void processGuiClicks(float tpf) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().processGuiClicks(tpf);
        }
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
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().notifyBlockSet(worldX, worldY, worldZ, blockId);
        }
    }

    /** Only the named pack's GuiApi may draw its own elements this frame; all others are gated off. */
    public void setOnlyDrawing(String packName) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            String name = e.getKey();
            e.getValue().setDraw(name.equals(packName) || isAlwaysOn(name));
        }
    }

    /** No pack may draw its own GuiApi elements this frame, except always-on utility packs. */
    public void disableAllDrawing() {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            e.getValue().setDraw(isAlwaysOn(e.getKey()));
        }
    }

    /**
     * Delivers a Mod.send(data) message from `fromPack` to every OTHER
     * pack's onReceive(data, fromModName), skipping disabled packs as
     * recipients (the sender itself is also skipped — no self-echo).
     */
    public void broadcast(String fromPack, String data) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            String name = e.getKey();
            if (name.equals(fromPack)) continue;
            if (disabledPacks.contains(name)) continue;
            e.getValue().deliverMessage(data, fromPack);
        }
    }

    public boolean isSaved(String packName) {
        return savedModsStore != null && savedModsStore.isSaved(packName);
    }

    public void setSaved(String packName, boolean saved) {
        if (savedModsStore != null) savedModsStore.setSaved(packName, saved);
    }

    /** Saved names filtered to packs that are currently loaded — stale entries (deleted/renamed mods) are hidden here but stay in the file. */
    public List<String> getSavedPackNames() {
        if (savedModsStore == null) return List.of();
        return savedModsStore.getSavedNames().stream()
                .filter(packs::containsKey)
                .collect(Collectors.toList());
    }

    /**
     * Notifies every enabled pack that a non-chunk spatial with the given name
     * was left-clicked. Packs use {@code Engine.onSpatialLeftClick(fn)} to
     * subscribe to these events.
     */
    public void notifySpatialLeftClick(String spatialName) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().notifySpatialLeftClick(spatialName);
        }
    }

    /**
     * Notifies every enabled pack that a non-chunk spatial with the given name
     * was right-clicked. Packs use {@code Engine.onSpatialRightClick(fn)} to
     * subscribe to these events.
     */
    public void notifySpatialRightClick(String spatialName) {
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().notifySpatialRightClick(spatialName);
        }
    }

    /** Flushes every pack's DataApi buffer to disk. Call once at app shutdown, from Main.destroy(). */
    public void onClose() {
        for (JSModifier m : packs.values()) {
            m.onClose();
        }
    }

    /**
     * Reloads a single mod pack: saves+closes the old instance, then creates a
     * fresh JSModifier and re-runs every .js file under worlds/<world>/mod/<packName>/.
     *
     * FLAG: any Scene nodes the mod created (Scene.createCube etc.) are NOT
     * auto-removed — they'll stay in the world unless the mod cleans up after
     * itself. Also, if the pack was disabled before reload, it comes back enabled
     * (packs.put() doesn't touch disabledPacks) — let me know if you'd rather it
     * stay disabled.
     */
    public void reloadPack(String packName) throws IOException {
        JSModifier oldMod = packs.get(packName);
        if (oldMod == null) {
            System.err.println("[ModPackManager] Pack '" + packName + "' not found, cannot reload");
            return;
        }
    
        Path dir = modRoot.resolve(packName); // same convention loadAll() used
        if (!Files.isDirectory(dir)) {
            System.err.println("[ModPackManager] Pack folder missing on disk: " + dir);
            return;
        }
    
        System.out.println("[ModPackManager] Reloading pack: " + packName);

        oldMod.onClose(); // flushes DataApi to disk BEFORE we throw the old Context away
        packs.remove(packName);

        JSModifier newMod = new JSModifier();
        newMod.init(cachedAssetManager, cachedRootNode, cachedWorldAccess,
                    cachedRenderManager, cachedCam, dir, packName, this, cachedInventory);

        try (var walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".js"))
                .sorted()
                .forEach(p -> {
                    try {
                        newMod.runJs(p.toFile());
                    } catch (Exception ex) {
                        Logger.getLogger(ModPackManager.class.getName()).log(Level.SEVERE,
                                "Failed to reload mod script '" + p + "' in pack '" + packName + "'", ex);
                    }
                });
        }

        packs.put(packName, newMod);
        System.out.println("[ModPackManager] Pack '" + packName + "' reloaded successfully");
    }
}
