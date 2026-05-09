package com.mygame;

import java.util.concurrent.ConcurrentHashMap;

public final class WorldAccess {

    public final ConcurrentHashMap<ChunkPos,BufferedChunk> Useful
            = new ConcurrentHashMap<>();

    private final ChunkFileHelper fileHelper;

    public WorldAccess(String worldFolder){
        fileHelper = new ChunkFileHelper(worldFolder);
    }

    public BufferedChunk getChunk(ChunkPos pos){
        return Useful.get(pos);
    }

    public BufferedChunk ensureChunk(ChunkPos pos){

        BufferedChunk c = Useful.get(pos);
        if(c!=null) return c;

        BufferedChunk loaded = fileHelper.loadChunk(pos);

        if(loaded!=null){
            Useful.put(pos,loaded);
            return loaded;
        }

        BufferedChunk created = new BufferedChunk(pos);

        Useful.put(pos,created);

        return created;
    }

    public void unloadChunk(ChunkPos pos){

        BufferedChunk c = Useful.remove(pos);

        if(c!=null)
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

    public void setBlockAt(int worldX, int worldY, int worldZ, int blockId) {
        ChunkPos chunkPos = worldToChunk(worldX, worldY, worldZ);
        BufferedChunk chunk = ensureChunk(chunkPos);
        int localX = worldToLocal(worldX);
        int localY = worldToLocal(worldY);
        int localZ = worldToLocal(worldZ);
        chunk.set(localX, localY, localZ, blockId);
    }

    public void removeBlockAt(int worldX, int worldY, int worldZ) {
        setBlockAt(worldX, worldY, worldZ, 0); // 0 = air/empty
    }

    // Helper methods for coordinate conversion
    private ChunkPos worldToChunk(int worldCoord) {
        return new ChunkPos(worldCoord >> 4, worldCoord >> 4, worldCoord >> 4); // Assuming SIZE=16, so >>4
    }

    private int worldToLocal(int worldCoord) {
        return worldCoord & 15; // &15 for SIZE=16
    }

    private ChunkPos worldToChunk(int worldX, int worldY, int worldZ) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    
    
}