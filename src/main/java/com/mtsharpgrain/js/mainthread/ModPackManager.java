package com.mtsharpgrain.js.mainthread;

import com.jme.igui.IGui;
import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;
import com.mtsharpgrain.gui.Inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import com.mtsharpgrain.BufferedChunk;
import com.mtsharpgrain.ChunkPos;
import com.mtsharpgrain.node.DynamicBlockRegistry;

 /**
 * Owns the runtime for every mod pack.
 *
 * <p>Background packs (default): one {@link JSModifier}, {@link ModBridge},
 * Graal context, and virtual thread each. Work is submitted from the render
 * thread; the virtual thread owns the Context.
 *
 * <p>MAIN packs (folder name ends with {@code MAIN}): same Context isolation,
 * but the Context runs on the render thread. Only the {@code "Update"} tick
 * tag (and optional top-level {@code update(tpf)}) is invoked. EngineAccess
 * calls are direct (no enqueue) because execution is already on main.
 * Inter-mod messaging still uses {@link ModBridge} so background and MAIN
 * packs can talk.
 *
 * <p>See {@code THREADING.md} in this package for details and best practices.
 */
public final class ModPackManager {

    private static final Logger LOG = Logger.getLogger(ModPackManager.class.getName());
    private static final String[] ALWAYS_ON_PREFIXES = {"LFT", "RHT", "BTM", "UTIL", "MODE"};

    /** All packs (background + MAIN) for lookup / broadcast / GUI. */
    private final Map<String, JSModifier> packs = new ConcurrentHashMap<>();
    /** Background packs only (virtual thread + mailbox). */
    private final Map<String, JSModifier> backgroundPacks = new ConcurrentHashMap<>();
    /** MAIN-suffix packs only (render thread, Update-only). */
    private final Map<String, JSModifier> mainPacks = new ConcurrentHashMap<>();
    private final Map<String, ModBridge> bridges = new ConcurrentHashMap<>();
    private final Set<String> disabledPacks = new CopyOnWriteArraySet<>();

    private volatile SavedModsStore savedModsStore;
    private volatile Path modRoot;
    private volatile AssetManager cachedAssetManager;
    private volatile Node cachedRootNode;
    private volatile WorldAccess cachedWorldAccess;
    private volatile RenderManager cachedRenderManager;
    private volatile Camera cachedCam;
    private volatile Inventory cachedInventory;
    private volatile EngineAccess engineAccess;
    private final ThreadLocal<String> currentPack = new ThreadLocal<>();
    private final Map<String, PackSchedule> schedules = new ConcurrentHashMap<>();

    private static final class PackSchedule {
        long nextTickNs;
        long nextUpdateNs;
    }

    private static final long TICK_INTERVAL_NS = 200_000_000L;
    private static final long UPDATE_INTERVAL_NS = 2_000_000_000L;

    public void loadAll(Path modRoot, AssetManager assetManager, Node rootNode,
                         WorldAccess worldAccess, RenderManager renderManager,
                         Camera cam, Inventory inventory) throws IOException {
        if (!Files.exists(modRoot)) {
            LOG.log(Level.WARNING, "Mod folder not found, skipping: " + modRoot.toAbsolutePath());
            return;
        }

        this.modRoot = modRoot;
        this.cachedAssetManager = assetManager;
        this.cachedRootNode = rootNode;
        this.cachedWorldAccess = worldAccess;
        this.cachedRenderManager = renderManager;
        this.cachedCam = cam;
        this.cachedInventory = inventory;
        if (engineAccess == null) {
            throw new IllegalStateException("ModPackManager requires EngineAccess before loadAll()");
        }

        Path worldFolder = modRoot.getParent() == null ? modRoot : modRoot.getParent();
        savedModsStore = new SavedModsStore(worldFolder);

        List<Path> packDirs;
        try (var stream = Files.list(modRoot)) {
            packDirs = stream.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        }

        // Register every mailbox before starting any script. This makes
        // Mod.send() during one pack's startup safe even if another pack has
        // not yet finished loading its files.
        List<Path> backgroundDirs = new ArrayList<>();
        List<Path> mainDirs = new ArrayList<>();
        for (Path dir : packDirs) {
            String packName = dir.getFileName().toString();
            boolean isMain = isMainSuffix(packName);
            JSModifier modifier = new JSModifier(isMain);
            ModBridge bridge = new ModBridge();
            modifier.attachBridge(bridge);
            packs.put(packName, modifier);
            bridges.put(packName, bridge);
            if (isMain) {
                mainPacks.put(packName, modifier);
                mainDirs.add(dir);
            } else {
                backgroundPacks.put(packName, modifier);
                backgroundDirs.add(dir);
            }
            PackSchedule schedule = new PackSchedule();
            long now = System.nanoTime();
            schedule.nextTickNs = now;
            schedule.nextUpdateNs = now;
            schedules.put(packName, schedule);
        }

        // Background packs: virtual thread owns Context + mailbox loop
        for (Path dir : backgroundDirs) {
            String packName = dir.getFileName().toString();
            JSModifier modifier = packs.get(packName);
            ModBridge bridge = bridges.get(packName);
            Thread.ofVirtual().name("mod-" + packName).start(() -> runPack(
                    dir, packName, modifier, bridge, assetManager, rootNode,
                    worldAccess, renderManager, cam, inventory));
        }

        // MAIN packs: load on the render thread (loadAll is called from simpleInitApp).
        // No virtual loop; ticks are driven by runUpdateDirect from tick().
        for (Path dir : mainDirs) {
            String packName = dir.getFileName().toString();
            JSModifier modifier = packs.get(packName);
            ModBridge bridge = bridges.get(packName);
            runMainPack(dir, packName, modifier, bridge, assetManager, rootNode,
                    worldAccess, renderManager, cam, inventory);
        }
    }

    /** Folder name ends with MAIN (case-sensitive) → render-thread pack. */
    private static boolean isMainSuffix(String packName) {
        return packName != null && packName.endsWith("MAIN");
    }

    public boolean isRenderThread() {
        return engineAccess != null && engineAccess.isMainThread();
    }

    private void runMainPack(Path dir, String packName, JSModifier modifier,
                             ModBridge bridge, AssetManager assetManager, Node rootNode,
                             WorldAccess worldAccess, RenderManager renderManager,
                             Camera cam, Inventory inventory) {
        try {
            modifier.init(assetManager, rootNode, worldAccess, renderManager, cam,
                    dir, packName, this, inventory, engineAccess);
            modifier.beginOwnerThread();
            try (var walk = Files.walk(dir)) {
                List<Path> scripts = walk.filter(path -> path.toString().endsWith(".js"))
                        .sorted().collect(Collectors.toList());
                for (Path script : scripts) {
                    try {
                        modifier.runJs(script.toFile());
                    } catch (Exception scriptError) {
                        LOG.log(Level.SEVERE, "Failed to load MAIN mod script '" + script
                                + "' in pack '" + packName + "'", scriptError);
                    }
                }
            }
            // Bind owner to this (render) thread and flush pending messages
            modifier.bindMainThread();
            LOG.info("MAIN pack loaded on render thread: " + packName);
        } catch (Throwable error) {
            modifier.markFailed();
            LOG.log(Level.SEVERE, "Failed to start MAIN mod pack '" + packName + "'", error);
            bridge.requestShutdown();
            modifier.abortStartup();
        } finally {
            modifier.endOwnerThread();
        }
    }

    private void runPack(Path dir, String packName, JSModifier modifier,
                         ModBridge bridge, AssetManager assetManager, Node rootNode,
                         WorldAccess worldAccess, RenderManager renderManager,
                         Camera cam, Inventory inventory) {
        try {
            modifier.init(assetManager, rootNode, worldAccess, renderManager, cam,
                    dir, packName, this, inventory, engineAccess);
            // init installs the context owner references. Only then may script
            // evaluation use the manager's owner-thread routing.
            modifier.beginOwnerThread();

            try (var walk = Files.walk(dir)) {
                List<Path> scripts = walk.filter(path -> path.toString().endsWith(".js"))
                        .sorted().collect(Collectors.toList());
                for (Path script : scripts) {
                    try {
                        modifier.runJs(script.toFile());
                    } catch (Exception scriptError) {
                        LOG.log(Level.SEVERE, "Failed to load mod script '" + script
                                + "' in pack '" + packName + "'", scriptError);
                    }
                }
            }
            modifier.startMainLoop(bridge);
        } catch (Throwable error) {
            modifier.markFailed();
            LOG.log(Level.SEVERE, "Failed to start mod pack '" + packName + "'", error);
            bridge.requestShutdown();
            modifier.abortStartup();
        } finally {
            modifier.endOwnerThread();
        }
    }

    void enterPack(String packName) {
        currentPack.set(packName);
    }

    void exitPack() {
        currentPack.remove();
    }

    public void setEngineAccess(EngineAccess engineAccess) {
        this.engineAccess = engineAccess;
    }

    public JSModifier getMod(String packName) {
        return packs.get(packName);
    }

    public Set<String> getPackNames() {
        return Collections.unmodifiableSet(packs.keySet());
    }

    public List<String> getSortedPackNames() {
        List<String> names = new ArrayList<>(packs.keySet());
        Collections.sort(names);
        return names;
    }

    public boolean isEnabled(String packName) {
        return packs.containsKey(packName) && !disabledPacks.contains(packName);
    }

    public void setEnabled(String packName, boolean enabled) {
        if (!packs.containsKey(packName)) return;

        if (enabled) {
            // If the pack was disabled, try to start a fresh runtime for it.
            // Reloading will create a replacement JSModifier and mailbox.
            if (disabledPacks.remove(packName)) {
                try {
                    reloadPack(packName);
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to reload mod pack '" + packName + "' when enabling", e);
                    // keep it disabled if reload failed
                    disabledPacks.add(packName);
                }
            }
        } else {
            // Mark disabled and request shutdown of the running runtime so it
            // stops receiving/processing work. The pack can be reloaded when
            // re-enabled via reloadPack above.
            if (!disabledPacks.contains(packName)) {
                disabledPacks.add(packName);
                JSModifier mod = packs.get(packName);
                if (mod != null) mod.shutdown();
            }
        }
    }

    /** Queues the per-frame callback; it never waits for a mod. */
    public void tick(float tpf, String guiTag) {
        long now = System.nanoTime();

        // Background packs: submit only (virtual threads own the Context)
        for (Map.Entry<String, JSModifier> entry : backgroundPacks.entrySet()) {
            String name = entry.getKey();
            if (disabledPacks.contains(name)) continue;
            JSModifier modifier = entry.getValue();
            modifier.submitTick(tpf, guiTag);

            PackSchedule schedule = schedules.computeIfAbsent(name, ignored -> {
                PackSchedule created = new PackSchedule();
                created.nextTickNs = now;
                created.nextUpdateNs = now;
                return created;
            });
            if (now >= schedule.nextTickNs) {
                modifier.submitTaggedTick(tpf, "Tick");
                schedule.nextTickNs = now + TICK_INTERVAL_NS;
            }
        }

        // MAIN packs: direct Update on render thread + drain inbound bridge work
        for (Map.Entry<String, JSModifier> entry : mainPacks.entrySet()) {
            String name = entry.getKey();
            if (disabledPacks.contains(name)) continue;
            JSModifier modifier = entry.getValue();
            if (!modifier.isInitialized()) continue;
            modifier.drainBridgeOnMain();
            // Exclusive Update path (no "Tick", no other tags from this manager)
            modifier.runUpdateDirect(tpf);
        }
    }

    public void tickAll(float tpf) {
        for (Map.Entry<String, JSModifier> entry : backgroundPacks.entrySet()) {
            if (!disabledPacks.contains(entry.getKey())) {
                entry.getValue().submitTickAll(tpf);
            }
        }
        // MAIN packs intentionally do not receive tick-all; they are Update-only.
    }

    /**
     * Draws Java-owned GUI state. This method does not execute JS and is safe
     * on the render thread even while a mod is blocked in EngineAccess.
     */
    public void draw(IGui gui) {
        for (Map.Entry<String, JSModifier> entry : packs.entrySet()) {
            if (!disabledPacks.contains(entry.getKey())) entry.getValue().draw(gui);
        }
    }

    public void processGuiClicks(float tpf) {
        for (Map.Entry<String, JSModifier> entry : packs.entrySet()) {
            if (!disabledPacks.contains(entry.getKey())) entry.getValue().processGuiClicks(tpf);
        }
    }

    /**
     * Returns a future for all enabled validators. The render thread only
     * checks this future later in WorldAccess.processPendingBlockChanges().
     * Uses non-blocking callbacks to avoid freezing virtual threads.
     */
    public CompletableFuture<Boolean> validateBlockChangeAsync(int worldX, int worldY, int worldZ, int blockId) {
        List<CompletableFuture<Boolean>> validations = new ArrayList<>();
        for (Map.Entry<String, JSModifier> entry : packs.entrySet()) {
            if (!disabledPacks.contains(entry.getKey())) {
                if (entry.getKey().equals(currentPack.get())) {
                    validations.add(CompletableFuture.completedFuture(
                            entry.getValue().validateBlockChangeOnOwnerThread(
                                    worldX, worldY, worldZ, blockId)));
                } else {
                    validations.add(entry.getValue().validateBlockChange(
                            worldX, worldY, worldZ, blockId));
                }
            }
        }
        if (validations.isEmpty()) return CompletableFuture.completedFuture(true);

        // Use non-blocking approach: collect results as they complete
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        int[] completedCount = {0};
        boolean[] allAllowed = {true};

        for (CompletableFuture<Boolean> validation : validations) {
            validation.whenComplete((allowed, ex) -> {
                synchronized (completedCount) {
                    completedCount[0]++;
                    if (ex != null) {
                        allAllowed[0] = false;
                    } else if (!allowed) {
                        allAllowed[0] = false;
                    }

                    // Check if all validations are complete
                    if (completedCount[0] == validations.size()) {
                        resultFuture.complete(allAllowed[0]);
                    }
                }
            });
        }

        return resultFuture;
    }

    /** Compatibility entry point; new callers should use validateBlockChangeAsync. */
    public void notifyBlockSet(int x, int y, int z, int blockId) {
        validateBlockChangeAsync(x, y, z, blockId);
    }

    public void setOnlyDrawing(String packName) {
        for (Map.Entry<String, JSModifier> entry : packs.entrySet()) {
            String name = entry.getKey();
            entry.getValue().setDraw(name.equals(packName) || isAlwaysOn(name));
        }
    }

    public void disableAllDrawing() {
        for (Map.Entry<String, JSModifier> entry : packs.entrySet()) {
            entry.getValue().setDraw(isAlwaysOn(entry.getKey()));
        }
    }

    public void broadcast(String fromPack, String data) {
        for (Map.Entry<String, JSModifier> entry : packs.entrySet()) {
            String name = entry.getKey();
            if (!name.equals(fromPack) && !disabledPacks.contains(name)) {
                entry.getValue().deliverMessage(data, fromPack);
            }
        }
    }

    public boolean isSaved(String packName) {
        SavedModsStore store = savedModsStore;
        return store != null && store.isSaved(packName);
    }

    public void setSaved(String packName, boolean saved) {
        SavedModsStore store = savedModsStore;
        if (store != null) store.setSaved(packName, saved);
    }

    public List<String> getSavedPackNames() {
        SavedModsStore store = savedModsStore;
        if (store == null) return List.of();
        return store.getSavedNames().stream()
                .filter(packs::containsKey).collect(Collectors.toList());
    }

    public void notifySpatialLeftClick(String spatialName) {
        notifySpatial(spatialName, true);
    }

    public void notifySpatialRightClick(String spatialName) {
        notifySpatial(spatialName, false);
    }

    private void notifySpatial(String spatialName, boolean left) {
        for (Map.Entry<String, JSModifier> entry : packs.entrySet()) {
            if (disabledPacks.contains(entry.getKey())) continue;
            if (left) entry.getValue().notifySpatialLeftClick(spatialName);
            else entry.getValue().notifySpatialRightClick(spatialName);
        }
    }

    /**
     * Reloads asynchronously. The old context is asked to save and close on
     * its own virtual thread; the replacement also loads scripts there.
     */
    public void reloadPack(String packName) throws IOException {
        JSModifier old = packs.get(packName);
        ModBridge oldBridge = bridges.get(packName);
        Path root = modRoot;
        if (old == null || oldBridge == null || root == null) return;

        Path dir = root.resolve(packName);
        if (!Files.isDirectory(dir)) return;
        old.shutdown();
        backgroundPacks.remove(packName);
        mainPacks.remove(packName);

        boolean isMain = isMainSuffix(packName);
        JSModifier replacement = new JSModifier(isMain);
        ModBridge bridge = new ModBridge();
        replacement.attachBridge(bridge);
        packs.put(packName, replacement);
        bridges.put(packName, bridge);
        if (isMain) {
            mainPacks.put(packName, replacement);
        } else {
            backgroundPacks.put(packName, replacement);
        }
        PackSchedule schedule = new PackSchedule();
        long now = System.nanoTime();
        schedule.nextTickNs = now;
        schedule.nextUpdateNs = now;
        schedules.put(packName, schedule);
        if (isMain) {
            // Reload MAIN packs on the render thread if possible; otherwise queue
            // via EngineAccess so we stay off foreign threads.
            Runnable work = () -> runMainPack(dir, packName, replacement, bridge,
                    cachedAssetManager, cachedRootNode, cachedWorldAccess,
                    cachedRenderManager, cachedCam, cachedInventory);
            if (isRenderThread()) {
                work.run();
            } else if (engineAccess != null) {
                engineAccess.post(work);
            } else {
                work.run();
            }
        } else {
            Thread.ofVirtual().name("mod-" + packName + "-reload").start(() -> runPack(
                    dir, packName, replacement, bridge, cachedAssetManager, cachedRootNode,
                    cachedWorldAccess, cachedRenderManager, cachedCam, cachedInventory));
        }
    }

    public void onClose() {
        for (JSModifier modifier : packs.values()) modifier.shutdown();
    }

    private static boolean isAlwaysOn(String packName) {
        for (String prefix : ALWAYS_ON_PREFIXES) {
            if (packName.startsWith(prefix)) return true;
        }
        return false;
    }

    public void notifyChunkLoaded(ChunkPos pos, BufferedChunk chunk) {
        if (chunk == null) return;
        // world-scoped registry available via cachedWorldAccess
        if (cachedWorldAccess == null) return;
        var registry = com.mtsharpgrain.node.DynamicBlockRegistry.getInstance();
        if (registry == null) return;

        // For each pack, submit an owner-thread task that scans the chunk and fires LOADED
        for (Map.Entry<String, JSModifier> en : packs.entrySet()) {
            final String packName = en.getKey();
            final JSModifier modifier = en.getValue();
            final ModBridge bridge = bridges.get(packName);
            if (bridge == null || modifier == null) continue;
            // Submit a small scanning task on the mod's owner thread
            bridge.submitTask(() -> {
                try {
                    // iterate local coords 0..15
                    for (int lx = 0; lx < BufferedChunk.SIZE; lx++) {
                        for (int ly = 0; ly < BufferedChunk.SIZE; ly++) {
                            for (int lz = 0; lz < BufferedChunk.SIZE; lz++) {
                                int block = chunk.get(lx, ly, lz);
                                var regOpt = registry.getById(block);
                                if (regOpt.isPresent()) {
                                    var reg = regOpt.get();
                                    if (packName.equals(reg.modPack())) {
                                        int worldX = pos.getX() * BufferedChunk.SIZE + lx;
                                        int worldY = pos.getY() * BufferedChunk.SIZE + ly;
                                        int worldZ = pos.getZ() * BufferedChunk.SIZE + lz;
                                        // call into the JS runtime on owner thread
                                        try {
                                            modifier.notifyBlockEventFromManager(worldX, worldY, worldZ, block, "LOADED");
                                        } catch (Throwable t) {
                                            System.err.println("[ModPackManager] notifyBlockEvent task failed: " + t.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("[ModPackManager] chunk scan failed: " + t.getMessage());
                }
            });
        }
    }

    /**
     * Called when a single block is placed/destroyed in the world. We resolve the
     * owning mod by registration and schedule a single owner-thread notify.
     */
    public void notifyBlockEvent(int worldX, int worldY, int worldZ, int blockId, String event) {
        if (cachedWorldAccess == null) return;
        var registry = com.mtsharpgrain.node.DynamicBlockRegistry.getInstance();
        if (registry == null) return;
        var regOpt = registry.getById(blockId);
        if (regOpt.isEmpty()) return;
        var reg = regOpt.get();
        String packName = reg.modPack();
        JSModifier modifier = packs.get(packName);
        ModBridge bridge = bridges.get(packName);
        if (modifier == null || bridge == null) return;
        bridge.submitTask(() -> {
            try {
                modifier.notifyBlockEventFromManager(worldX, worldY, worldZ, blockId, event);
            } catch (Throwable t) {
                System.err.println("[ModPackManager] notifyBlockEvent failed on owner thread: " + t.getMessage());
            }
        });
    }
}
