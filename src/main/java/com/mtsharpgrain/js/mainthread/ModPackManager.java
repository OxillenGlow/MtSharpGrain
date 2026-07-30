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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
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

    // --- Scheduled/staggered update system -------------------------------------------------
    // Intervals and budget (ns)
    private static final long UPDATE_INTERVAL_NS = 2_000_000_000L; // 2s
    private static final long TICK_INTERVAL_NS = 200_000_000L;     // 0.2s
    private static final long TIME_BUDGET_NS = 10_000_000L;       // 10ms per frame budget for scheduled work
    private static final int MAX_CATCHUP_FACTOR = 5;               // if behind > factor*interval, skip catchup
    private static final int FAILURE_THRESHOLD = 5;               // disable pack after N consecutive failures

    private final Map<String, PackSchedule> schedules = new LinkedHashMap<>();
    private final Random jitter = new Random();
    private int tickCarryIndex = 0;   // round-robin continuation index for tick phase
    private int updateCarryIndex = 0; // round-robin continuation index for update phase

    private static final long MIN_CALL_COST_NS = 500_000L; // 0.5ms guard to avoid starting tiny calls when no budget

    private static final class PackSchedule {
        long nextTickDueNs;
        long nextUpdateDueNs;
        long lastTickNs;
        long lastUpdateNs;
        int consecutiveFailures = 0;
    }

    // --------------------------------------------------------------------------------------

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

        // build initial schedules with staggered offsets so work is distributed
        recomputeStaggers();
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

    /**
     * Per-frame tick called from Main.simpleUpdate. We preserve existing
     * per-frame behaviour (run every pack's tick callbacks), and additionally
     * run the scheduled/staggered coarse tick + infrequent update passes.
     */
    public void tick(float tpf, String guiTag) {
        // Preserve existing behaviour first (per-frame ticks remain unchanged).
        for (Map.Entry<String, JSModifier> e : packs.entrySet()) {
            if (disabledPacks.contains(e.getKey())) continue;
            e.getValue().tick(tpf, guiTag);
        }

        // Run scheduled work (coarse ticks and infrequent updates) on the main thread.
        scheduledStep();
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

        // Recompute staggers when packs change
        recomputeStaggers();
    }

    // --------------------------------------------------------------------------------------
    // Scheduling helpers

    private void recomputeStaggers() {
        schedules.clear();
        List<String> names = new ArrayList<>(packs.keySet());
        int n = names.size();
        long now = System.nanoTime();
        if (n == 0) return;

        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            PackSchedule s = new PackSchedule();
            // stagger offsets so work spreads across the window
            long updateOffset = (i * UPDATE_INTERVAL_NS) / n;
            long tickOffset = (i * TICK_INTERVAL_NS) / n;
            // small jitter +/-10ms to avoid pathological alignment
            long j1 = (jitter.nextLong() % 20_000_000L);
            long j2 = (jitter.nextLong() % 5_000_000L);
            updateOffset += j1 - 10_000_000L;
            tickOffset += j2 - 2_500_000L;

            s.nextUpdateDueNs = now + Math.max(0, updateOffset);
            s.nextTickDueNs = now + Math.max(0, tickOffset);
            s.lastTickNs = now;
            s.lastUpdateNs = now;
            schedules.put(name, s);
        }

        // reset carry indices so we start from a stable point
        tickCarryIndex = 0;
        updateCarryIndex = 0;
    }

    /** Main scheduled step called once per frame from tick(). */
    private void scheduledStep() {
        if (packs.isEmpty()) return;
        long now = System.nanoTime();
        long budget = TIME_BUDGET_NS;

        List<String> names = new ArrayList<>(packs.keySet());
        int n = names.size();

        // Phase 1: coarse ticks (every ~0.2s) with tag "Tick"
        for (int checked = 0; checked < n; checked++) {
            int idx = (tickCarryIndex + checked) % n;
            String name = names.get(idx);
            PackSchedule s = schedules.get(name);
            if (s == null) continue; // shouldn't happen but be safe
            if (disabledPacks.contains(name)) continue;

            if (now >= s.nextTickDueNs) {
                long age = now - s.nextTickDueNs;
                if (age > TICK_INTERVAL_NS * MAX_CATCHUP_FACTOR) {
                    // too far behind, skip catchup and reschedule
                    s.nextTickDueNs = now + TICK_INTERVAL_NS;
                    s.lastTickNs = now;
                    continue;
                }

                if (budget < MIN_CALL_COST_NS) {
                    // not enough budget to start another scheduled call
                    break;
                }

                try {
                    long tStart = System.nanoTime();
                    // pass elapsed tpf since last tick to the pack's tagged callbacks
                    float tpfForPack = (float) ((now - s.lastTickNs) / 1_000_000_000.0);
                    packs.get(name).tick(tpfForPack, "Tick");
                    long took = System.nanoTime() - tStart;
                    budget -= took;

                    s.lastTickNs = now;
                    s.nextTickDueNs = now + TICK_INTERVAL_NS;
                    s.consecutiveFailures = 0;
                } catch (Exception ex) {
                    // guard pack from repeatedly failing
                    s.consecutiveFailures++;
                    System.err.println("[ModPackManager] scheduled tick for '" + name + "' threw: " + ex.getMessage());
                    if (s.consecutiveFailures >= FAILURE_THRESHOLD) {
                        System.err.println("[ModPackManager] disabling pack '" + name + "' after " + s.consecutiveFailures + " failures");
                        disabledPacks.add(name);
                    }
                }
            }

            // advance carry index so we continue fairly next frame
            tickCarryIndex = (idx + 1) % n;
        }

        // Phase 2: infrequent updates (every ~2s) — now calls tickAll and is tagged "Update"
        now = System.nanoTime(); // refresh time
        for (int checked = 0; checked < n; checked++) {
            int idx = (updateCarryIndex + checked) % n;
            String name = names.get(idx);
            PackSchedule s = schedules.get(name);
            if (s == null) continue;
            if (disabledPacks.contains(name)) continue;

            if (now >= s.nextUpdateDueNs) {
                long age = now - s.nextUpdateDueNs;
                if (age > UPDATE_INTERVAL_NS * MAX_CATCHUP_FACTOR) {
                    // too far behind, skip catchup and reschedule
                    s.nextUpdateDueNs = now + UPDATE_INTERVAL_NS;
                    s.lastUpdateNs = now;
                    continue;
                }

                if (budget < MIN_CALL_COST_NS) {
                    break; // no budget this frame
                }

                try {
                    long tStart = System.nanoTime();
                    float tpfForPack = (float) ((now - s.lastUpdateNs) / 1_000_000_000.0);
                    // Use tickAll for the Update phase per your request
                    packs.get(name).tickAll(tpfForPack);
                    long took = System.nanoTime() - tStart;
                    budget -= took;

                    s.lastUpdateNs = now;
                    s.nextUpdateDueNs = now + UPDATE_INTERVAL_NS;
                    s.consecutiveFailures = 0;
                } catch (Exception ex) {
                    s.consecutiveFailures++;
                    System.err.println("[ModPackManager] scheduled Update for '" + name + "' threw: " + ex.getMessage());
                    if (s.consecutiveFailures >= FAILURE_THRESHOLD) {
                        System.err.println("[ModPackManager] disabling pack '" + name + "' after " + s.consecutiveFailures + " failures");
                        disabledPacks.add(name);
                    }
                }
            }

            updateCarryIndex = (idx + 1) % n;
        }
    }
}
