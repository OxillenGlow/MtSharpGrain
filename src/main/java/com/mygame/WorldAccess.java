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

    
    
}