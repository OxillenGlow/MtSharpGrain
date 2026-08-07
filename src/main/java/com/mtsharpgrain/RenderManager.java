package com.mtsharpgrain;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.mtsharpgrain.js.JsChunkGenerator;
import java.util.concurrent.*;
import java.util.*;

public final class RenderManager {

    private final WorldAccess worldAccess;
    private final SimpleApplication app; // REQUIRED for enqueue
    private final ConcurrentHashMap<ChunkPos, ChunkRenderData> renderMap = new ConcurrentHashMap<>();
    private final Queue<ChunkPos> dirtyQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkPos> pendingChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int viewTop = 1;
    private int viewBottom = 1;
    Player player;
    Node nd;
    AssetManager assetManager;
    private final Set<ChunkPos> dirtySet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<ChunkPos> pendingGeneration = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int chunksRequestedThisFrame = 0;
    private static final int MAX_CHUNK_REQUESTS_PER_FRAME = 8; // Throttle chunk requests

    // These are now wired in via the constructor instead of being left unset.
    // chunkGen is the SAME JsChunkGenerator instance passed into WorldAccess,
    // so there is exactly one GraalVM Context/genThread for the whole world.
    private final JsChunkGenerator chunkGen;
    private final long worldSeed;

    public RenderManager(WorldAccess worldAccess, Node nd, AssetManager am, Player player,
                          SimpleApplication app, JsChunkGenerator chunkGen, long worldSeed) {
        this.worldAccess = worldAccess;
        this.nd = nd;
        this.assetManager = am;
        this.player = player;
        this.app = app;
        this.chunkGen = chunkGen;
        this.worldSeed = worldSeed;
    }

    private void requestChunk(ChunkPos pos) {
        // If we are already tracking/rendering this chunk, do nothing.
        if (renderMap.containsKey(pos)) return;
        // If the mesh is already attached to the scene (safety), skip creating render data.
        String meshName = "Ck" + pos.getX() + "y" + pos.getY() + "z" + pos.getZ();
        if (nd.getChild(meshName) != null) return;

        BufferedChunk loaded = worldAccess.getChunk(pos);
        if (loaded != null) {
            // Only create render data and mark dirty if we don't already have it.
            renderMap.computeIfAbsent(pos, p -> { markDirty(p); return new ChunkRenderData(p); });
            return;
        }
        if (!pendingGeneration.add(pos)) return; // already in flight
        
        BufferedChunk fromDisk = worldAccess.tryLoadFromDisk(pos);
        if (fromDisk != null) {
            worldAccess.putLoadedChunk(pos, fromDisk);
            pendingGeneration.remove(pos);
            renderMap.computeIfAbsent(pos, p -> { markDirty(p); return new ChunkRenderData(p); });

            return;
        }
        
        // Async path: runs chunkBuild() in chunkgen.js on the dedicated js-chunk-gen thread.
        // Non-blocking - the main/render thread is free to keep ticking while this resolves.
        chunkGen.generateAsync(pos, worldSeed).whenComplete((chunk, err) -> {
            pendingGeneration.remove(pos);
            if (err != null) { err.printStackTrace(); return; }
            worldAccess.putLoadedChunk(pos, chunk);
            renderMap.computeIfAbsent(pos, p -> { markDirty(p); return new ChunkRenderData(p); });
        });
    }

    private int lastPx = Integer.MIN_VALUE, lastPy, lastPz;
    Set<ChunkPos> stillInRange = new HashSet<>();

    public void tick(float playerX, float playerY, float playerZ) {
        int px = worldToChunk((int)playerX);
        int py = worldToChunk((int)playerY);
        int pz = worldToChunk((int)playerZ);

        // Reset request counter at the start of each frame
        chunksRequestedThisFrame = 0;

        stillInRange.clear();

        // Check if player has moved to a new chunk
        boolean playerMoved = (px != lastPx || py != lastPy || pz != lastPz);
        lastPx = px;
        lastPy = py;
        lastPz = pz;

        // Prioritize chunks near the player when moving
        int startDx = (playerMoved) ? -1 : -Main.VIEW_DISTANCE;
        int endDx = (playerMoved) ? 1 : Main.VIEW_DISTANCE;
        int startDz = (playerMoved) ? -1 : -Main.VIEW_DISTANCE;
        int endDz = (playerMoved) ? 1 : Main.VIEW_DISTANCE;

        // First pass: request chunks in priority order (closer to player first)
        for (int priority = 0; priority <= 1; priority++) {
            int dxStart = (priority == 0) ? startDx : -Main.VIEW_DISTANCE;
            int dxEnd = (priority == 0) ? endDx : Main.VIEW_DISTANCE;
            int dzStart = (priority == 0) ? startDz : -Main.VIEW_DISTANCE;
            int dzEnd = (priority == 0) ? endDz : Main.VIEW_DISTANCE;

            for (int dx = dxStart; dx <= dxEnd; dx++) {
                for (int dy = -viewBottom; dy <= viewTop; dy++) {
                    for (int dz = dzStart; dz <= dzEnd; dz++) {
                        ChunkPos pos = new ChunkPos(px + dx, py + dy, pz + dz);
                        stillInRange.add(pos);

                        // Throttle: only request if we haven't hit the limit
                        if (chunksRequestedThisFrame < MAX_CHUNK_REQUESTS_PER_FRAME) {
                            requestChunk(pos);
                            chunksRequestedThisFrame++;
                        }
                    }
                }
            }
        }

        // Unload anything that's no longer in the view-distance window
        for (ChunkPos loaded : renderMap.keySet()) {
            if (!stillInRange.contains(loaded)) {
                unloadChunk(loaded);
            }
        }

        this.processDirtyQueue();
    }

    private static int worldToChunk(int coord) {
        return coord >> 4; // match WorldAccess exactly
    }

    public void markDirty(ChunkPos pos) {
        if (dirtySet.add(pos)) {  // add() returns false if already present - O(1)
            dirtyQueue.add(pos);
        }
    }

    public void markNeighborsDirty(ChunkPos pos) {
        markDirty(pos);
        markDirty(pos.add(1, 0, 0));
        markDirty(pos.add(-1, 0, 0));
        markDirty(pos.add(0, 1, 0));
        markDirty(pos.add(0, -1, 0));
        markDirty(pos.add(0, 0, 1));
        markDirty(pos.add(0, 0, -1));
    }

    private void processDirtyQueue() {
        int maxPerTick = 8;
        for (int i = 0; i < maxPerTick; i++) {
            ChunkPos pos = dirtyQueue.poll();
            if (pos == null) return;
            if (!renderMap.containsKey(pos)) continue;
            if (pendingChunks.contains(pos)) {
                dirtyQueue.add(pos);
                continue;
            }
            BufferedChunk chunk = worldAccess.getChunk(pos);
            if (chunk == null) continue;

            // Skip rebuild if chunk data hasn't changed (hash comparison)
            ChunkRenderData crd = renderMap.get(pos);
            int currentHash = chunk.hashCode();
            if (crd.lastChunkHash != null && crd.lastChunkHash.equals(currentHash)) {
                dirtySet.remove(pos);
                continue; // Chunk unchanged, skip rebuild
            }

            CompletableFuture.runAsync(() -> {
                try {
                    dirtySet.remove(pos);
                    pendingChunks.add(pos);
                    Spatial newMesh = ChunkMeshBuilder.build(pos, chunk, assetManager);
                    app.enqueue(() -> {
                        if (!renderMap.containsKey(pos)) {
                            pendingChunks.remove(pos);
                            return null;
                        }
                        Spatial oldCk = nd.getChild(newMesh.getName());
                        if (oldCk != null) oldCk.removeFromParent();
                        nd.attachChild(newMesh);
                        ChunkRenderData currentData = renderMap.get(pos);
                        if (currentData != null) {
                            currentData.lastBuiltTime = System.currentTimeMillis();
                            currentData.lastChunkHash = currentHash;
                        }
                        pendingChunks.remove(pos);
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    pendingChunks.remove(pos);
                    System.out.println("AHHH");
                }
            });
        }
    }

    public void unloadChunk(ChunkPos pos) {
        renderMap.remove(pos);
        dirtySet.remove(pos);
        worldAccess.unloadChunk(pos);
        app.enqueue(() -> {
            Spatial s = nd.getChild("Ck" + pos.getX() + "y" + pos.getY() + "z" + pos.getZ());
            if (s != null) s.removeFromParent();
            return null;
        });
    }

    public void onBlockChanged(int worldX, int worldY, int worldZ) {
        ChunkPos chunkPos = worldToChunk(worldX, worldY, worldZ);
        markDirty(chunkPos);
        markNeighborsDirty(chunkPos);
    }

    public static final class ChunkRenderData {
        public Object geometry;
        public long lastBuiltTime;
        public ChunkPos pos;
        public Integer lastChunkHash;
        public ChunkRenderData(ChunkPos pos) { this.pos = pos; }
    }

    private static ChunkPos worldToChunk(int x, int y, int z) {
        return new ChunkPos(worldToChunk(x), worldToChunk(y), worldToChunk(z));
    }
}