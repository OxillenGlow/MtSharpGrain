package com.mtsharpgrain;

import com.mtsharpgrain.js.mainthread.ModPackManager;
import com.mtsharpgrain.js.JsChunkGenerator;
import com.mtsharpgrain.gui.Inventory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.List;

public final class WorldAccess {

    public final ConcurrentHashMap<ChunkPos,BufferedChunk> Useful = new ConcurrentHashMap<>();
    private final ChunkFileHelper fileHelper;
    private final JsChunkGenerator generator;
    private final long seed;
    private ModPackManager modPackManager;
    private Inventory inventory;
    private final ConcurrentLinkedQueue<PendingBlockChange> pendingChanges = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<int[]> committedChanges = new ConcurrentLinkedQueue<>();
    public static long t;
    private RenderManager renderManager;

    void setRenderManager(com.mtsharpgrain.RenderManager renderManager) {
        this.renderManager = renderManager;
    }

    
    private static final class PendingBlockChange {
        final int x, y, z, blockId;
        CompletableFuture<Boolean> validation;
        // result of the validator when it completes; written by the validator callback.
        volatile Boolean validationResult = null;
        // true once the validator completion handler has run or we've abandoned the validator.
        volatile boolean validationHandled = false;
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        final long createdAt;
    
        PendingBlockChange(int x, int y, int z, int blockId, CompletableFuture<Boolean> validation) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.validation = validation;
            this.createdAt = System.currentTimeMillis();  // Record time
        }
    }

    
    public WorldAccess(String worldFolder, JsChunkGenerator generator, long seed){
        fileHelper = new ChunkFileHelper(worldFolder);
        this.generator = generator;
        this.seed = seed;
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
    public CompletableFuture<Boolean> requestBlockChange(int worldX, int worldY, int worldZ, int blockId) {
        CompletableFuture<Boolean> placeholder = new CompletableFuture<>();

        if (modPackManager == null) {
            placeholder.complete(true);
        } else {
            // dispatch the validation request off the render thread into a tiny virtual
            // thread so that any unexpected slow work inside validateBlockChangeAsync
            // cannot stall this call site.
            Thread.startVirtualThread(() -> {
                try {
                    CompletableFuture<Boolean> real = modPackManager.validateBlockChangeAsync(
                            worldX, worldY, worldZ, blockId);
                    real.whenComplete((v, ex) -> {
                        if (ex != null) placeholder.completeExceptionally(ex);
                        else placeholder.complete(v);
                    });
                } catch (Throwable t) {
                    placeholder.completeExceptionally(t);
                }
            });
        }

        PendingBlockChange change = new PendingBlockChange(worldX, worldY, worldZ, blockId, placeholder);

        // Install a completion handler so we can observe the validator's outcome
        // without ever blocking the render thread. The handler records the result
        // into the PendingBlockChange and marks validationHandled; the render thread
        // reads those fields later and never calls join()/get() itself.
        placeholder.whenComplete((v, ex) -> {
            change.validationResult = (ex == null && Boolean.TRUE.equals(v));
            change.validationHandled = true;
        });

        pendingChanges.offer(change);
        return change.result;
    }

    /**
     * Commits validation-complete changes on the render thread. A validator
     * result is never applied from a mod virtual thread.
     */
    public void processPendingBlockChanges() {
        long now = System.currentTimeMillis();
        int count = pendingChanges.size();
        for (int i = 0; i < count; i++) {
            PendingBlockChange change = pendingChanges.poll();
            if (change == null) break;

            long elapsed = now - change.createdAt;
            this.t = elapsed/40;
            if (elapsed > 4000) {  // 4 seconds
                // If validator still hasn't completed, abandon it and allow GC to collect.
                if (change.validation != null && !change.validation.isDone()) {
                    change.result.complete(true);
                    // Drop the reference to the validator future so the future and any
                    // captured JS/Graal state can be GC'd. Mark handled so we won't
                    // try to read the result later.
                    change.validation = null;
                    change.validationHandled = true;
                    continue;
                }
            } else {
                // If validator hasn't completed yet, requeue and skip applying.
                if (change.validation != null && !change.validation.isDone()) {
                    pendingChanges.offer(change);
                    continue;
                }
            }
            // Determine the validation outcome without blocking the render thread.
            boolean allowed;
            if (change.validationHandled) {
                allowed = Boolean.TRUE.equals(change.validationResult);
            } else if (change.validation != null && change.validation.isDone()) {
                // Safety: getNow never blocks.
                try {
                    allowed = change.validation.getNow(false);
                } catch (Throwable t) {
                    allowed = false;
                }
            } else {
                // No validator present (cleared on timeout) -> treat as rejected.
                allowed = false;
            }
            
            if (!allowed) {
                change.result.complete(false);
                continue;
            }

            ChunkPos chunkPos = worldToChunk(change.x, change.y, change.z);
            BufferedChunk chunk = ensureChunk(chunkPos);
            int localX = worldToLocal(change.x);
            int localY = worldToLocal(change.y);
            int localZ = worldToLocal(change.z);
            System.out.println("gona check inventory");
            if (inventory != null && !inventory.handleBlockChange(
                    chunk.get(localX, localY, localZ), change.blockId)) {
                change.result.complete(false);
                continue;
            }
            
            chunk.set(localX, localY, localZ, change.blockId);
            System.out.println("gona notify RM");
            this.renderManager.onBlockChanged(change.x, change.y, change.z);
            committedChanges.offer(new int[]{change.x, change.y, change.z});
            change.result.complete(true);
        }
    }


    /** Returns successful edits since the last render-frame drain. */
    public List<int[]> drainCommittedChanges() {
        List<int[]> result = new ArrayList<>();
        int[] change;
        while ((change = committedChanges.poll()) != null) result.add(change);
        return result;
    }

    public CompletableFuture<Boolean> requestRemoveBlock(int worldX, int worldY, int worldZ) {
        return requestBlockChange(worldX, worldY, worldZ, 0);
    }

    /** Legacy game-side entry point. It intentionally does not wait. */
    public void setBlockAt(int worldX, int worldY, int worldZ, int blockId) {
        requestBlockChange(worldX, worldY, worldZ, blockId);
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
    public void forceSetBlockAt(int worldX, int worldY, int worldZ, int blockId) {
        ChunkPos chunkPos = worldToChunk(worldX, worldY, worldZ);
        BufferedChunk chunk = ensureChunk(chunkPos); // generates/loads if not already resident
        int localX = worldToLocal(worldX);
        int localY = worldToLocal(worldY);
        int localZ = worldToLocal(worldZ);
        chunk.set(localX, localY, localZ, blockId);
    }
}
