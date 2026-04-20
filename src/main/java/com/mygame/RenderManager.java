package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.concurrent.*;
import java.util.*;

public final class RenderManager {

    private final WorldAccess worldAccess;
    private final SimpleApplication app; // REQUIRED for enqueue
    private final ConcurrentHashMap<ChunkPos, ChunkRenderData> renderMap = new ConcurrentHashMap<>();
    private final Queue<ChunkPos> dirtyQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkPos> pendingChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int viewDistance = 1;
    private static final double CHUNK_METERS = BufferedChunk.SIZE * 0.5;
    private int viewHeight = -1;
    Player player;
    Node nd;
    AssetManager assetManager;

    public RenderManager(WorldAccess worldAccess, Node nd, AssetManager am, Player player, SimpleApplication app) {
        this.worldAccess = worldAccess;
        this.nd = nd;
        this.assetManager = am;
        this.player = player;
        this.app = app;
    }

    
    public void tick(float playerX, float playerY, float playerZ) {
    int px = worldToChunk(playerX);
    int py = worldToChunk(playerY);
    int pz = worldToChunk(playerZ);

        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dy = -viewDistance; dy <= viewHeight; dy++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    ChunkPos pos = new ChunkPos(px + dx, py + dy, pz + dz);

                    worldAccess.ensureChunk(pos);

                    // FIX: If this is the FIRST time we see this chunk, mark it dirty
                    renderMap.computeIfAbsent(pos, p -> {
                        markDirty(p); // Trigger a build for the new chunk
                    return new ChunkRenderData(p);
                    });
                }
            }
        }
        this.processDirtyQueue();
    }

    private static int worldToChunk(double meters) {
        return (int) Math.floor(meters / CHUNK_METERS);
    }

    public void markDirty(ChunkPos pos) {
        if (!dirtyQueue.contains(pos)) dirtyQueue.add(pos);
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
        ChunkPos pos = dirtyQueue.poll();
        if (pos == null || pendingChunks.contains(pos)) return;

        BufferedChunk chunk = worldAccess.getChunk(pos);
        if (chunk == null) return;

        pendingChunks.add(pos);

        // --- MULTITHREADING START (Using Java's default ForkJoinPool or Virtual Threads) ---
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Heavy Building (Background Thread)
                Spatial newMesh = ChunkMeshBuilder.build(pos, chunk, assetManager);
                ChunkUnloadControl ctr = new ChunkUnloadControl(this, pos, player);
                newMesh.addControl(ctr);

                // 2. Scene Graph Sync (Back to Main JME Thread)
                app.enqueue(() -> {
                    Spatial oldCk = nd.getChild(newMesh.getName());
                    if (oldCk != null) oldCk.removeFromParent();
                    
                    nd.attachChild(newMesh);
                    
                    ChunkRenderData crd = renderMap.get(pos);
                    if (crd != null) crd.lastBuiltTime = System.currentTimeMillis();
                    
                    pendingChunks.remove(pos); // Clean up tracking
                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
                pendingChunks.remove(pos);
            }
        });
    }

    public void unloadChunk(ChunkPos pos) {
        renderMap.remove(pos);
        worldAccess.unloadChunk(pos);
        // Enqueue removal to ensure thread safety with the Node
        app.enqueue(() -> {
            Spatial s = nd.getChild("Ck" + pos.getX() + "y" + pos.getY() + "z" + pos.getZ());
            if (s != null) s.removeFromParent();
            return null;
        });
    }

    public static final class ChunkRenderData {
        public Object geometry;
        public long lastBuiltTime;
        public ChunkPos pos;
        public ChunkRenderData(ChunkPos pos) { this.pos = pos; }
    }
}
