package com.mtsharpgrain.node;

import com.jme3.math.ColorRGBA;

/**
 * Central lookup table for all block types.
 *
 * HOW TO ADD A NEW BLOCK TYPE
 * ───────────────────────────
 *  1. Append a new BlockDef entry to the BLOCKS array below.
 *     The array index IS the block ID, so keep them in order.
 *  2. Give it a descriptive comment so the next dev knows what it is.
 *  3. Add the ID as a named constant at the top of this class (optional but recommended).
 *
 * BLOCK IDs
 *   0  – air  (never rendered)
 *   1  – reserved / transparent  (never rendered)
 *   2  – Stone
 *   3  – Dirt
 *   4  – Grass
 *   5  – Crystal Ore
 *   6+ – add yours here
 *
 * AMBIENT is shared across all block types and lives here so there is
 * exactly one place to change it.
 */
public final class BlockRegistry {

    // ── Shared ambient (same for every block) ──────────────────────────────
    public static final ColorRGBA AMBIENT = ColorRGBA.fromRGBA255(5, 5, 15, 255);

    // ── Named ID constants (add one when you add a new block) ──────────────
    public static final int ID_AIR          = 0;
    public static final int ID_RESERVED     = 1;
    public static final int ID_STONE        = 2;
    public static final int ID_DIRT         = 3;
    public static final int ID_GRASS        = 4;
    public static final int ID_CRYSTAL_ORE  = 5;

    // ── Block definition ───────────────────────────────────────────────────

    /**
     * Holds the material colours and shininess for one block type.
     * Ambient is NOT stored here; it comes from {@link BlockRegistry#AMBIENT}.
     *
     * @param diffuse   the primary surface colour under light
     * @param specular  the highlight colour (use {@code ColorRGBA.Black} for matte)
     * @param shininess Phong shininess exponent; 0 = matte, 128 = mirror-like
     */
    public record BlockDef(ColorRGBA diffuse, ColorRGBA specular, float shininess, boolean mostlyAir) {

        /** Convenience constructor for fully matte blocks with no specular highlight. */
        public BlockDef(ColorRGBA diffuse) {
            this(diffuse, ColorRGBA.Black, 0f, false);
        }
    }

    // ── Lookup table ───────────────────────────────────────────────────────
    // Index == block ID.  null entries are treated as "unknown / fallback".
    private static final BlockDef[] BLOCKS = {

        /* 0 – air      */ null,
        /* 1 – reserved */ null,

        /* 2 – Stone: mid-grey, low specular gloss */
        new BlockDef(
            ColorRGBA.fromRGBA255(120, 120, 125, 255),   // diffuse
            ColorRGBA.fromRGBA255( 55,  55,  60, 255),   // specular
            24f,// shininess
            false
        ),

        /* 3 – Dirt: earthy brown, fully matte */
        new BlockDef(
            ColorRGBA.fromRGBA255(101,  67,  33, 255)    // diffuse (matte shortcut)
        ),

        /* 4 – Grass: forest green, subtle wet-leaf sheen */
        new BlockDef(
            ColorRGBA.fromRGBA255( 34, 139,  34, 255),   // diffuse
            ColorRGBA.fromRGBA255( 15,  60,  15, 255),   // specular
            16f,                                          // shininess
            false
        ),

        /* 5 – Crystal Ore: electric blue, high specular sparkle */
        new BlockDef(
            ColorRGBA.fromRGBA255( 70, 110, 210, 255),   // diffuse
            ColorRGBA.fromRGBA255(200, 225, 255, 255),   // specular
            112f,                                         // shininess
            false
        ),

        // ── ADD NEW BLOCKS BELOW THIS LINE ────────────────────────────────
        // Example:
        //   /* 6 – Lava: deep orange, no specular (molten look) */
        //   new BlockDef(
        //       ColorRGBA.fromRGBA255(210, 80, 10, 255)
        //   ),
    };

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns the {@link BlockDef} for the given block ID, or {@code null}
     * if the ID is out of range or not yet defined (treat as unknown).
     */
    public static BlockDef get(int id) {
        if (id < 0 || id >= BLOCKS.length) return null;
        return BLOCKS[id];
    }

    /**
     * Returns {@code true} if the block ID represents empty / transparent space
     * that should never be rendered.
     */
    public static boolean isAir(int id) {
        return id == ID_AIR || id == ID_RESERVED;
    }

    // Prevent instantiation — this is a pure static registry.
    private BlockRegistry() {}
}
