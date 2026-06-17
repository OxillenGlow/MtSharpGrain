package com.mtsharpgrain;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
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
    private int viewHeight = 0;
    Player player;
    Node nd;
    AssetManager assetManager;
    private final Set<ChunkPos> dirtySet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RenderManager(WorldAccess worldAccess, Node nd, AssetManager am, Player player, SimpleApplication app) {
        this.worldAccess = worldAccess;
        this.nd = nd;
        this.assetManager = am;
        this.player = player;
        this.app = app;
    }

    public void tick(float playerX, float playerY, float playerZ) {
        int px = worldToChunk((int)playerX);
        int py = worldToChunk((int)playerY);
        int pz = worldToChunk((int)playerZ);
        
        for (int dx = -Main.VIEW_DISTANCE; dx <= Main.VIEW_DISTANCE; dx++) {
            for (int dy = -Main.VIEW_DISTANCE; dy <= viewHeight; dy++) {
                for (int dz = -Main.VIEW_DISTANCE; dz <= Main.VIEW_DISTANCE; dz++) {
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

    private static int worldToChunk(int coord) {
        return coord >> 4; // match WorldAccess exactly
    }

    public void markDirty(ChunkPos pos) {
        if (dirtySet.add(pos)) {  // add() returns false if already present — O(1)
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
        int maxPerTick = 1;
        for (int i = 0; i < maxPerTick; i++) {
            ChunkPos pos = dirtyQueue.poll();
            if (pos == null) return;                    // queue empty, fine to stop
            if (pendingChunks.contains(pos)) {
                dirtyQueue.add(pos);
                continue;
            }
            BufferedChunk chunk = worldAccess.getChunk(pos);
            if (chunk == null) continue;
            // --- MULTITHREADING START (Using Java's default ForkJoinPool or Virtual Threads) ---
            CompletableFuture.runAsync(() -> {
                try {
                    dirtySet.remove(pos);  // keep them in sync

                    pendingChunks.add(pos);

                    // Building (Background Thread in enqueue)
                    Spatial newMesh = ChunkMeshBuilder.build(pos, chunk, assetManager);
                    //ChunkUnloadControl ctr = new ChunkUnloadControl(this, pos, player);
                    //newMesh.addControl(ctr);
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
                    System.out.println("AHHH");
                }
            });
        }
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

    // New method to handle block changes and trigger rebuilds
    public void onBlockChanged(int worldX, int worldY, int worldZ) {
        ChunkPos chunkPos = worldToChunk(worldX, worldY, worldZ);
        markDirty(chunkPos);
        // Mark neighbors if on boundary (simplified: always mark neighbors for now)
        markNeighborsDirty(chunkPos);
    }

    public static final class ChunkRenderData {
        public Object geometry;
        public long lastBuiltTime;
        public ChunkPos pos;
        public ChunkRenderData(ChunkPos pos) { this.pos = pos; }
    }

    private static ChunkPos worldToChunk(int x, int y, int z) {
        return new ChunkPos(worldToChunk(x), worldToChunk(y), worldToChunk(z));
    }
}
