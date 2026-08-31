package com.mtsharpgrain;

import com.mtsharpgrain.js.mainthread.ModPackManager;
import com.mtsharpgrain.js.JsChunkGenerator;
import com.mtsharpgrain.gui.Inventory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.mtsharpgrain.node.DynamicBlockRegistry;
import com.mtsharpgrain.storage.BlockValuesStore;
import static java.lang.System.out;
 

public final class WorldAccess {

    public final ConcurrentHashMap<ChunkPos,BufferedChunk> Useful = new ConcurrentHashMap<>();
    private final ChunkFileHelper fileHelper;
    private final JsChunkGenerator generator;
    private final long seed;
    private ModPackManager modPackManager;
    private Inventory inventory;
    private final ConcurrentLinkedQueue<PendingBlockChange> pendingChanges = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<int[]> committedChanges = new ConcurrentLinkedQueue<>();
    public static int percent;
    private RenderManager renderManager;
    private final Path worldFolderPath;
    
    void setRenderManager(com.mtsharpgrain.RenderManager renderManager) {
        this.renderManager = renderManager;
    }

    // IMPORTANT SUBCLASS
    private static final class PendingBlockChange {
        final int x, y, z, blockId;
        CompletableFuture<Boolean> validation;
        // result of the validator when it completes; written by the validator callback.
        volatile Boolean validationResult = true;
        // true once the validator completion handler has run or we've abandoned the validator.
        volatile boolean validationHandled = false;
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        final long createdAt;
    
        PendingBlockChange(int x, int y, int z, int blockId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.createdAt = System.currentTimeMillis();  // Record time
        }
    }

    
    public WorldAccess(String worldFolder, JsChunkGenerator generator, long seed){
        fileHelper = new ChunkFileHelper(worldFolder);
        this.generator = generator;
        this.seed = seed;
        this.worldFolderPath = Paths.get(worldFolder);
        try {
            DynamicBlockRegistry.init(worldFolderPath);
            BlockValuesStore.init(worldFolderPath);
        } catch (Exception e) {
            System.err.println("[WorldAccess] failed to initialize registries: " + e.getMessage());
        }
    }

    public void addModifier(ModPackManager modPackManager){
        this.modPackManager = modPackManager;
    }

    /** Wires the core inventory into setBlockAt's gate. Safe to leave unset (interception simply no-ops). */
    public void setInventory(Inventory inventory){
        this.inventory = inventory;
    }
    
    

    public BufferedChunk ensureChunk(ChunkPos pos){
        BufferedChunk c = Useful.get(pos);
        if(c!=null) return c;

        BufferedChunk loaded = fileHelper.loadChunk(pos);
        if(loaded!=null){
            Useful.put(pos,loaded);
            return loaded;
        }

        // Was: new BufferedChunk(pos)  -> now actually runs chunkBuild() in chunkgen.js
        BufferedChunk generated = generator.generateSync(pos, seed);
        Useful.put(pos, generated);
        fileHelper.saveChunk(pos, generated);
        return generated;
    }

    public BufferedChunk getChunk(ChunkPos pos){
        return Useful.get(pos);
    }

    public void unloadChunk(ChunkPos pos){

        BufferedChunk c = Useful.remove(pos);

        if(c!=null && pos.getX() > -6 )
            fileHelper.saveChunk(pos,c);
    }
    
    public void createChunkAt(ChunkPos pos, int blockId) {
        BufferedChunk newChunk = new BufferedChunk(blockId);
        Useful.put(pos, newChunk);
        fileHelper.saveChunk(pos, newChunk); // If you want it on disk immediately
    }
    public void saveAll() {
        System.out.println("Saving world...");
        // Iterate through all loaded chunks in your 'Useful' map
        // Replace 'Useful' with the actual name of your HashMap if different
        Useful.forEach((pos, chunk) -> {
            fileHelper.saveChunk(pos, chunk);
        });
        System.out.println("Save complete.");
    }

    // New methods for block editing
    public int getBlockAt(int worldX, int worldY, int worldZ) {
        ChunkPos chunkPos = worldToChunk(worldX, worldY, worldZ);
        BufferedChunk chunk = ensureChunk(chunkPos);
        int localX = worldToLocal(worldX);
        int localY = worldToLocal(worldY);
        int localZ = worldToLocal(worldZ);
        return chunk.get(localX, localY, localZ);
    }

    /**
     * Requests a validated block change. Validation runs on mod virtual
     * threads; the actual world mutation is committed by
     * {@link #processPendingBlockChanges()} on the render thread.
     */
    public void requestBlockChange(int worldX, int worldY, int worldZ, int blockId) {
        if (modPackManager == null) {
            // No validation available – add change immediately
            pendingChanges.offer(new PendingBlockChange(worldX, worldY, worldZ, blockId));
            return;
        }

        // Dispatch validation request off the render thread.
        Thread.startVirtualThread(() -> {
            CompletableFuture<Boolean> validationFuture =
                modPackManager.validateBlockChangeAsync(worldX, worldY, worldZ, blockId);

            PendingBlockChange change = new PendingBlockChange(worldX, worldY, worldZ, blockId);
            change.validation = validationFuture;

            // Attach handler to mark handled/result when future completes
            validationFuture.whenComplete((result, ex) -> {
                change.validationHandled = true;
                change.validationResult = (ex == null) && Boolean.TRUE.equals(result);
            });

            pendingChanges.offer(change);
        });
}

    /**
     * Commits validation-complete changes on the render thread. A validator
     * result is never applied from a mod virtual thread.
     */
    public void processPendingBlockChanges() {
        int total = pendingChanges.size();
        if (total == 0) {
            percent = 0;
            return;
        }

        // Peek at the head of the queue without removing it.
        // If it is not ready, we break and return immediately.
        for (var change: pendingChanges) {
            if (change == null) continue;

            percent = (int) ((System.currentTimeMillis() - change.createdAt)/20);
            // Determine if this change is ready to be processed without blocking
            boolean ready = percent > 100;
            out.println(percent);
            if (!ready) {
                continue;
            }

            // Remove it now that it's ready
            pendingChanges.poll();

            // Figure out whether the change is allowed
            boolean allowed = determineAllowed(change);

            if (!allowed) {
                change.result.complete(false);
                continue;
            }

            // Inventory check (existing dumb patch)
            var pre = getBlockAt(change.x, change.y, change.z);
            if (!this.inventory.handleBlockChange(pre, change.blockId)) {
                change.result.complete(false);
                continue;
            }

            forceSetBlockAt(change.x, change.y, change.z, change.blockId);
            this.renderManager.onBlockChanged(change.x, change.y, change.z);
            committedChanges.offer(new int[]{change.x, change.y, change.z});

            if (modPackManager != null) {
                try {
                    String ev = (change.blockId == 0) ? "DESTROYED" : "PLACED";
                    modPackManager.notifyBlockEvent(change.x, change.y, change.z, change.blockId, ev);
                } catch (Throwable t) {
                    System.err.println("[WorldAccess] notifyBlockEvent failed: " + t.getMessage());
                }
            }
            percent=0;
            change.result.complete(true);
        }

        // Reset percent after this processing pass
        percent = 0;
    }

    /**
     * Returns true if the change's validation has completed or the timeout
     * of 2 seconds has elapsed, meaning we can decide without blocking.
     */
    private boolean isChangeReady(PendingBlockChange change) {
        if (change.validation == null) {
            // No validator – treat as rejected (original behavior) but ready to process
            return true;
        }
        if (change.validationHandled) {
            return true;
        }
        // If not handled, check if the future is done
        if (change.validation.isDone()) {
            return true;
        }
        // If future not done, check if 2 seconds have passed since creation
        long elapsed = System.currentTimeMillis() - change.createdAt;
        return elapsed >= 2000;
    }

    /**
     * Determines the allowed flag without blocking. Assumes the change is ready.
     */
    private boolean determineAllowed(PendingBlockChange change) {
        if (change.validation == null) {
            return false; // original logic treated no validator as rejected
        }
        if (change.validationHandled) {
            return Boolean.TRUE.equals(change.validationResult);
        }
        // If not handled but ready, then either future is done or timeout exceeded.
        if (change.validation.isDone()) {
            try {
                return change.validation.getNow(false);
            } catch (Throwable t) {
                return false;
            }
        } else {
            // Timeout exceeded – accept by default
            return true;
        }
    }


    /** Returns successful edits since the last render-frame drain. */
    public List<int[]> drainCommittedChanges() {
        List<int[]> result = new ArrayList<>();
        int[] change;
        while ((change = committedChanges.poll()) != null) result.add(change);
        return result;
    }

    public void requestRemoveBlock(int worldX, int worldY, int worldZ) {
        requestBlockChange(worldX, worldY, worldZ, 0);
    }

    /** Legacy game-side entry point. It intentionally does not wait. */
    public void setBlockAt(int worldX, int worldY, int worldZ, int blockId) {
        
        requestBlockChange(worldX, worldY, worldZ, blockId);
        System.out.println("REQUESTED");
    }

    /** Legacy game-side entry point. It intentionally does not wait. */
    public void removeBlockAt(int worldX, int worldY, int worldZ) {
        requestRemoveBlock(worldX, worldY, worldZ);
    }

    private int worldToLocal(int worldCoord) {
        return worldCoord & 15; // &15 for SIZE=16
    }

    private ChunkPos worldToChunk(int worldX, int worldY, int worldZ) {
        // Shifts coordinates by 4 (divides by 16) to find the chunk index
        return new ChunkPos(worldX >> 4, worldY >> 4, worldZ >> 4);
    }
    public BufferedChunk tryLoadFromDisk(ChunkPos pos) {
        return fileHelper.loadChunk(pos);
    }

    public void putLoadedChunk(ChunkPos pos, BufferedChunk chunk) {
        Useful.put(pos, chunk);
        // Notify mod manager that a chunk was just loaded so mods can reconnect
        if (modPackManager != null) {
            try {
                modPackManager.notifyChunkLoaded(pos, chunk);
            } catch (Throwable t) {
                System.err.println("[WorldAccess] notifyChunkLoaded failed: " + t.getMessage());
            }
        }
    }

    /**
     * Bypasses modPackManager.notifyBlockSet() validation entirely — for mod
     * code that needs to force a block change regardless of what any pack's
     * onBlockChange validators say (e.g. world-gen assist, admin tools, a mod
     * correcting its own prior placement). Unlike setBlockAt(), this can also
     * write into an unloaded chunk (via ensureChunk) instead of silently no-op'ing.
     *
     * FLAG: since this skips validators, it also skips whatever protections
     * those validators exist to enforce (griefing prevention, etc. if you ever
     * add that). Only wire this to trusted/internal call paths.
     */
    public int forceSetBlockAt(int worldX, int worldY, int worldZ, int blockId) {
        ChunkPos chunkPos = worldToChunk(worldX, worldY, worldZ);
        BufferedChunk chunk = ensureChunk(chunkPos); // generates/loads if not already resident
        int localX = worldToLocal(worldX);
        int localY = worldToLocal(worldY);
        int localZ = worldToLocal(worldZ);
        var pre = chunk.get(localX, localY, localZ);
        chunk.set(localX, localY, localZ, blockId);
        return pre;
    }
}
