package com.mtsharpgrain;

import com.mtsharpgrain.js.mainthread.JSModifier;
import com.mtsharpgrain.js.JsChunkGenerator;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldAccess {

    public final ConcurrentHashMap<ChunkPos,BufferedChunk> Useful = new ConcurrentHashMap<>();
    private final ChunkFileHelper fileHelper;
    private final JsChunkGenerator generator;
    private final long seed;
    private JSModifier modifier;

    public WorldAccess(String worldFolder, JsChunkGenerator generator, long seed){
        fileHelper = new ChunkFileHelper(worldFolder);
        this.generator = generator;
        this.seed = seed;
    }

    public addModifier(JSModifier modifier){
        this.modifier = modifier;
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

    public void setBlockAt(int worldX, int worldY, int worldZ, int blockId) {
        ChunkPos chunkPos = worldToChunk(worldX, worldY, worldZ);
        BufferedChunk chunk = ensureChunk(chunkPos);
        int localX = worldToLocal(worldX);
        int localY = worldToLocal(worldY);
        int localZ = worldToLocal(worldZ);
        
        modifier.notifyBlockPlaced(worldX,worldY,worldZ);
        
        chunk.set(localX, localY, localZ, blockId);
    }

    public void removeBlockAt(int worldX, int worldY, int worldZ) {
        setBlockAt(worldX, worldY, worldZ, 0); // 0 = air/empty
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
}
