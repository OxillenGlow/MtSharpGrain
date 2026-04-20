package com.mygame;

public final class BufferedChunk {

    public static final int SIZE = 16;
    private final int[][][] blocks;

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
        int fill = pos.getY() > 0 ? 1 : 2;
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
    }

    public int[][][] getRaw(){
        return blocks;
    }
}