package com.mtsharpgrain;

import java.util.Arrays;
import java.util.Objects;

public final class BufferedChunk {

    public static final int SIZE = 16;
    private final int[][][] blocks;
    private volatile Integer hashCodeCache = null; // Lazy hash cache

    public BufferedChunk(){
        blocks = new int[SIZE][SIZE][SIZE];
    }

    public BufferedChunk(int fill){
        this();
        for(int x=0;x<SIZE;x++)
            for(int y=0;y<SIZE;y++)
                for(int z=0;z<SIZE;z++)
                    blocks[x][y][z]=fill;
    }

    public BufferedChunk(ChunkPos pos){
        blocks = new int[SIZE][SIZE][SIZE];
        var fill = (pos.getY() > -1) ? 1 : 2;

        for(int x=0;x<SIZE;x++)
            for(int y=0;y<SIZE;y++)
                for(int z=0;z<SIZE;z++)
                    blocks[x][y][z]=fill;
    }

    public int get(int x,int y,int z){
        return blocks[x][y][z];
    }

    public void set(int x,int y,int z,int id){
        blocks[x][y][z]=id;
        hashCodeCache = null; // Invalidate cache on mutation
    }

    public int[][][] getRaw(){
        return blocks;
    }

    public void setFromFlat(int[] flat) {
        int i = 0;
        for (int x = 0; x < SIZE; x++)
        for (int y = 0; y < SIZE; y++)
        for (int z = 0; z < SIZE; z++)
            blocks[x][y][z] = flat[i++];
        hashCodeCache = null; // Invalidate cache on bulk load
    }

    public int[] toFlat() {
        int[] flat = new int[SIZE * SIZE * SIZE];
        int i = 0;
        for (int x = 0; x < SIZE; x++)
        for (int y = 0; y < SIZE; y++)
        for (int z = 0; z < SIZE; z++)
            flat[i++] = blocks[x][y][z];
        return flat;
    }

    // Hashing support for detecting unchanged chunks
    @Override
    public int hashCode() {
        if (hashCodeCache != null) {
            return hashCodeCache;
        }
        int result = Objects.hash(Arrays.deepHashCode(blocks));
        hashCodeCache = result;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BufferedChunk other = (BufferedChunk) obj;
        return Arrays.deepEquals(blocks, other.blocks);
    }
}