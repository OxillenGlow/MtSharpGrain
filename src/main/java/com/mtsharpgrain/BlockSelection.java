package com.mtsharpgrain;

/**
 * Represents a selected block position and the intended action.
 * Replaces the Object[] return from BlockSelector for better type safety.
 */
public class BlockSelection {
    public final int x, y, z;
    public final boolean placeAction; // true for place/build, false for remove

    public BlockSelection(int x, int y, int z, boolean placeAction) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.placeAction = placeAction;
    }

    @Override
    public String toString() {
        return "BlockSelection{x=" + x + ", y=" + y + ", z=" + z + ", action=" + (placeAction ? "place" : "remove") + "}";
    }
}