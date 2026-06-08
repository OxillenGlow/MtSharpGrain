package com.mtsharpgrain;

import java.util.Objects;

public final class ChunkPos {

    public final int x;
    public final int y;
    public final int z;

    public ChunkPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ChunkPos add(int dx, int dy, int dz) {
        return new ChunkPos(x + dx, y + dy, z + dz);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos)) return false;
        ChunkPos p = (ChunkPos) o;
        return x == p.x && y == p.y && z == p.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "ChunkPos[" + x + "," + y + "," + z + "]";
    }
}