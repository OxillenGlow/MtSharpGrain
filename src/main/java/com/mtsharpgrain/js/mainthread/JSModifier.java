package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;
import com.mtsharpgrain.gui.Inventory;
import com.mtsharpgrain.js.BlockApi;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime for exactly one mod pack.
 *
 * <p>The context is created, used, and closed by one virtual thread. Java
 * callers never invoke a Graal {@link Value} directly; they submit a task to
 * this runtime's {@link ModBridge}. Java APIs exposed to the script use
 * {@link EngineAccess} when they need jME/world state, so a blocked script
 * suspends its virtual thread instead of the render loop.
 */
public final class JSModifier {

    private volatile JsApiBootstrap bootstrap;
    private volatile ModBridge bridge;
    private volatile Thread runtimeThread;
    private volatile boolean initialized;
    private volatile boolean failed;
    private ModPackManager ownerManager;
    private String packName;
    private final ConcurrentLinkedQueue<PendingMessage> pendingMessages =
            new ConcurrentLinkedQueue<>();

    private record PendingMessage(String data, String fromPack) {}

    /** Installs the mailbox before init so messages can be buffered during startup. */
    public void attachBridge(ModBridge bridge) {
        this.bridge = bridge;
    }

    public void init(AssetManager assetManager, Node rootNode, WorldAccess worldAccess,
                     RenderManager renderManager, Camera cam, Path packDir,
                     String packName, ModPackManager modPackManager,
                     Inventory inventory, EngineAccess engineAccess) {
        this.ownerManager = modPackManager;
        this.packName = packName;
        WorldAccessor worldAccessor = new RealWorldAccessor(worldAccess);
        BlockApi blockApi = new BlockApi(worldAccess, renderManager, engineAccess);
        JsApiBootstrap created = new JsApiBootstrap(
                assetManager, worldAccessor, blockApi, cam, rootNode, packDir,
                packName, modPackManager, inventory, engineAccess);
        created.getNodeRegistry().registerFixed(0L, rootNode);
        bootstrap = created;
        initialized = true;
    }

    /** Evaluates one script. Must be called by this pack's owning virtual thread. */
    public void runJs(File scriptFile) throws IOException {
        requireRuntimeThread();
        if (!scriptFile.isFile()) {
            throw new IOException("Script file not found: " + scriptFile.getAbsolutePath());
        }
        try {
            bootstrap.getContext().eval(Source.newBuilder("js", scriptFile).build());
        } catch (PolyglotException e) {
            System.err.println("[JSModifier] error running script '" + scriptFile.getName()
                    + "': " + e.getMessage());
            throw e;
        }
    }

    /**
     * Runs the mailbox loop on the current virtual thread. The manager calls
     * this only after init() and alphabetical script loading have completed.
     */
    public void startMainLoop(ModBridge bridge) {
        this.bridge = bridge;
        this.runtimeThread = Thread.currentThread();
        try {
            // Messages received before init() or while scripts were loading must
            // be delivered only after all scripts have had a chance to define
            // onReceive(). This also keeps those callbacks on the owner thread.
            PendingMessage pending;
            while ((pending = pendingMessages.poll()) != null) {
                deliverMessageOnOwnerThread(pending.data(), pending.fromPack());
            }

            while (!bridge.shouldStop()) {
                Runnable task;
                try {
                    task = bridge.takeTask();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (bridge.isPoison(task)) break;
                try {
                    ownerManager.enterPack(packName);
                    task.run();
                } catch (Throwable error) {
                    System.err.println("[JSModifier] task failed: " + error.getMessage());
                } finally {
                    ownerManager.exitPack();
                }
            }
        } finally {
            try {
                if (bootstrap != null) bootstrap.getDataApi().save();
            } catch (Throwable error) {
                System.err.println("[JSModifier] failed to save data: " + error.getMessage());
            }
            try {
                if (bootstrap != null) bootstrap.getContext().close();
            } catch (Throwable error) {
                System.err.println("[JSModifier] failed to close context: " + error.getMessage());
            }
        }
    }

    public CompletableFuture<Void> submitTick(float tpf, String tag) {
        return submit(() -> {
            bootstrap.getTickRegistry().tickTag(tpf, tag);
            callOptionalUpdate(tpf);
        });
    }

    public CompletableFuture<Void> submitTickAll(float tpf) {
        return submit(() -> {
            bootstrap.getTickRegistry().tick(tpf);
        });
    }

    /** Runs a tagged callback without invoking the optional per-frame update hook. */
    public CompletableFuture<Void> submitTaggedTick(float tpf, String tag) {
        return submit(() -> bootstrap.getTickRegistry().tickTag(tpf, tag));
    }

    private void callOptionalUpdate(float tpf) {
        Value update = bootstrap.getContext().getBindings("js").getMember("update");
        if (update != null && update.canExecute()) {
            try {
                update.execute(tpf);
            } catch (PolyglotException e) {
                System.err.println("[JSModifier] update() threw: " + e.getMessage());
            }
        }
    }

    public CompletableFuture<Boolean> validateBlockChange(int x, int y, int z, int blockId) {
        return submit(() -> bootstrap.getBlockChangeRegistry()
                .checkBlockChange(x, y, z, blockId));
    }

    /** Called only by this pack's owning virtual thread to avoid self-queue deadlock. */
    boolean validateBlockChangeOnOwnerThread(int x, int y, int z, int blockId) {
        requireRuntimeThread();
        return bootstrap.getBlockChangeRegistry().checkBlockChange(x, y, z, blockId);
    }

    void beginOwnerThread() {
        runtimeThread = Thread.currentThread();
        ownerManager.enterPack(packName);
    }

    void endOwnerThread() {
        if (ownerManager != null) ownerManager.exitPack();
    }

    public CompletableFuture<Void> deliverMessage(String data, String fromPack) {
        if (!initialized) {
            if (failed || bridge == null) {
                CompletableFuture<Void> rejected = new CompletableFuture<>();
                rejected.completeExceptionally(new IllegalStateException("Mod runtime is not ready"));
                return rejected;
            }
            pendingMessages.offer(new PendingMessage(data, fromPack));
            return CompletableFuture.completedFuture(null);
        }
        return submit(() -> {
            deliverMessageOnOwnerThread(data, fromPack);
        });
    }

    private void deliverMessageOnOwnerThread(String data, String fromPack) {
        requireRuntimeThread();
        Value fn = bootstrap.getContext().getBindings("js").getMember("onReceive");
        if (fn != null && fn.canExecute()) fn.execute(data, fromPack);
    }

    public CompletableFuture<Void> notifySpatialLeftClick(String name) {
        return submit(() -> bootstrap.getSpatialClickRegistry().notifyLeftClick(name));
    }

    public CompletableFuture<Void> notifySpatialRightClick(String name) {
        return submit(() -> bootstrap.getSpatialClickRegistry().notifyRightClick(name));
    }

    public CompletableFuture<Void> notifyGuiClick(float tpf, String tag) {
        return submit(() -> bootstrap.getTickRegistry().tickTag(tpf, tag));
    }

    private <T> CompletableFuture<T> submit(java.util.concurrent.Callable<T> callable) {
        ModBridge currentBridge = bridge;
        if (currentBridge == null || !initialized || failed) {
            CompletableFuture<T> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new IllegalStateException("Mod runtime is not ready"));
            return rejected;
        }
        return currentBridge.submitCallable(callable);
    }

    private CompletableFuture<Void> submit(Runnable task) {
        
        ModBridge currentBridge = bridge;
        if (currentBridge == null || !initialized || failed) {
            CompletableFuture<Void> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new IllegalStateException("Mod runtime is not ready"));
            return rejected;
        }
        return currentBridge.submitCallable(() -> {
            task.run();
            return null;
        });
    }

    public GuiApi getGuiApi() {
        JsApiBootstrap current = bootstrap;
        return current == null ? null : current.getGuiApi();
    }

    /** Render-thread-only Java GUI draw; it does not enter Graal. */
    public void draw(com.jme.igui.IGui gui) {
        GuiApi api = getGuiApi();
        if (api != null) api.draw(gui);
    }

    /** Render-thread-only GUI click drain; callbacks are queued to the pack. */
    public void processGuiClicks(float tpf) {
        GuiApi api = getGuiApi();
        if (api == null) return;
        for (String tag : api.drainClickedTags()) notifyGuiClick(tpf, tag);
    }

    /** Render-thread-only draw gate; it only changes Java GUI state. */
    public void setDraw(boolean draw) {
        GuiApi api = getGuiApi();
        if (api != null) api.setDraw(draw);
    }

    public boolean isInitialized() {
        return initialized && !failed;
    }

    public void markFailed() {
        failed = true;
    }

    /** Requests asynchronous save/context shutdown; the render loop is not blocked. */
    public void shutdown() {
        ModBridge currentBridge = bridge;
        if (currentBridge != null) currentBridge.requestShutdown();
        Thread currentThread = runtimeThread;
        if (currentThread != null) currentThread.interrupt();
    }

    /** Closes a context when startup failed before the mailbox loop began. */
    void abortStartup() {
        try {
            if (bootstrap != null) bootstrap.getContext().close();
        } catch (Throwable error) {
            System.err.println("[JSModifier] failed to close startup context: " + error.getMessage());
        }
    }

    private void requireRuntimeThread() {
        if (runtimeThread != null && Thread.currentThread() != runtimeThread) {
            throw new IllegalStateException("Graal context accessed outside its owning mod thread");
        }
        if (!initialized) throw new IllegalStateException("Mod runtime is not initialized");
    }

    /**
     * Called by ModPackManager on the owning mod's virtual thread (via ModBridge.submitTask)
     * to notify the pack of a block event that originated on the render/world thread.
     */
    public void notifyBlockEventFromManager(int worldX, int worldY, int worldZ, int blockId, String event) {
        // Must be running on the owner thread (ModBridge ensures that in the caller).
        if (bootstrap == null) return;
        try {
            // delegate to the per-pack block loader registry
            bootstrap.getBlockLoaderRegistry().notifyEvent(blockId, packName, event, worldX, worldY, worldZ);
        } catch (Throwable t) {
            System.err.println("[JSModifier] notifyBlockEventFromManager failed: " + t.getMessage());
        }
    }
}
