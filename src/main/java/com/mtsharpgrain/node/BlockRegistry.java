package com.mtsharpgrain.node;

import com.jme3.math.ColorRGBA;

/**
 * Central lookup table for all block types.
 *
 * HOW TO ADD A NEW BLOCK TYPE
 * ───────────────────────────
 *  1. Append a new BlockDef entry to the BLOCKS array below.
 *     The array index IS the block ID, so keep them in order.
 *  2. Give it a descriptive comment so the next I know what it is.
 *  3. Add the ID as a named constant at the top of this class (optional but recommended).
 *
 * BLOCK IDs
 *   0  – air  (never rendered)
 *   1  – reserved / transparent  (never rendered)
 *   2  – Stone
 *   3  – Dirt
 *   4  – Grass
 *   5  – Crystal Ore
 *   6  - Ice Sludge
 *   7  - Silicon
 *   8  - Sulfur
 *   9  - Metal Block
 *   10 - Glass
 *   11 - Plant
 *   12 - Alien Plant
 *   13 - Wooden Stool
 *   14 - Metal Stool
 *   15 - Wooden Flat
 *   16 - Wooden Tall
 *   17 - Metal Flat
 *   18 - Metal Tall
 *   19 - Plastic Cube
 *   20 - Plastic Tiny
 *   21 - Plastic Tall
 *   22 - Plastic Flat
 *   23 - Plastic Stool
 *   24 - Plastic Plant
 *   25 - Plastic Py
 *   26 - Wooden Tiny
 *   27 - Metal Tiny
 *   28 - Cushion Flat
 *   29 - Crystal Tall
 *   +  – add yours here
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
    public static final int ID_ICE_SLUDGE   = 6;
    public static final int ID_SILICON      = 7;
    public static final int ID_SULFUR       = 8;
    public static final int ID_METAL_BLOCK  = 9;
    public static final int ID_GLASS        = 10;

    public static final int ID_PLANT            = 11;
    public static final int ID_ALIEN_PLANT      = 12;
    public static final int ID_WOODEN_STOOL     = 13;
    public static final int ID_METAL_STOOL      = 14;
    public static final int ID_WOODEN_FLAT      = 15;
    public static final int ID_WOODEN_TALL      = 16;
    public static final int ID_METAL_FLAT       = 17;
    public static final int ID_METAL_TALL       = 18;
    public static final int ID_PLASTIC_CUBE     = 19;
    public static final int ID_PLASTIC_TINY     = 20;
    public static final int ID_PLASTIC_TALL     = 21;
    public static final int ID_PLASTIC_FLAT     = 22;
    public static final int ID_PLASTIC_STOOL    = 23;
    public static final int ID_PLASTIC_PLANT    = 24;
    public static final int ID_PLASTIC_PY       = 25;
    public static final int ID_WOODEN_TINY      = 26;
    public static final int ID_METAL_TINY       = 27;
    public static final int ID_CUSHION_FLAT     = 28;
    public static final int ID_CRYSTAL_TALL     = 29;


    // ── Block definition ───────────────────────────────────────────────────

    /**
     * Holds the material colours and shininess for one block type.
     * Ambient is NOT stored here; it comes from {@link BlockRegistry#AMBIENT}.
     *
     * @param diffuse   the primary surface colour under light
     * @param specular  the highlight colour (use {@code ColorRGBA.Black} for matte)
     * @param shininess Phong shininess exponent; 0 = matte, 128 = mirror-like
     * @param mostlyAir not to be confused with is air, mostly air just gives whether node is solid
     */
    public record BlockDef(ColorRGBA diffuse, ColorRGBA specular, float shininess, boolean mostlyAir, String meshBuilder) {

        /** Convenience constructor for fully matte blocks with no specular highlight. */
        public BlockDef(ColorRGBA diffuse) {
            this(diffuse, ColorRGBA.Black, 0f, false, "Py");
        }
    }

    // ── Lookup table ───────────────────────────────────────────────────────
    // Index == block ID.  null entries are treated as "unknown / fallback".
    private static final BlockDef[] BLOCKS = {

        /* 0 – air      */ null,
        /* 1 – reserved */ null,

        /* 2 – Stone: slightly greyer mid-grey, low specular gloss */
        new BlockDef(
            ColorRGBA.fromRGBA255( 70,  65,  65, 255),   // diffuse (greyer than before)
            ColorRGBA.fromRGBA255( 95,  90,  90, 255),   // specular
            30f,// shininess
            false,
            "Py"
        ),

        /* 3 – Dirt: earthy brown, fully matte */
        new BlockDef(
            ColorRGBA.fromRGBA255(40,  10,  10, 255)    // diffuse (matte shortcut)
        ),

        /* 4 – Grass: forest green, subtle wet-leaf sheen */
        new BlockDef(
            ColorRGBA.fromRGBA255( 34, 139,  34, 255),   // diffuse
            ColorRGBA.fromRGBA255( 15,  60,  15, 255),   // specular
            16f,                                          // shininess
            false,
            "Py"
        ),

        /* 5 – Crystal Ore: electric blue, high specular sparkle */
        new BlockDef(
            ColorRGBA.fromRGBA255( 130, 150, 210, 255),   // diffuse
            ColorRGBA.fromRGBA255(200, 225, 255, 255),   // specular
            112f,                                         // shininess
            false,
            "CubeTiny"
        ),

        /* 6 – Ice sludge: Blue white */
        new BlockDef(
            ColorRGBA.fromRGBA255( 110, 110, 210, 255),   // diffuse
            ColorRGBA.fromRGBA255(30, 30, 40, 255),   // specular
            24f,                                         // shininess
            false,
            "Py"
        ),

        /* 7 – Silicon: dark bluish-grey, semi-reflective wafer look */
        new BlockDef(
            ColorRGBA.fromRGBA255( 60,  65,  75, 255),   // diffuse
            ColorRGBA.fromRGBA255(120, 130, 150, 255),   // specular
            70f,                                          // shininess
            false,
            "Py"
        ),

        /* 8 – Sulfur: bright matte yellow */
        new BlockDef(
            ColorRGBA.fromRGBA255(216, 200,  40, 255)    // diffuse (matte shortcut)
        ),

        /* 9 – Metal Block: grey iron, extremely shiny */
        new BlockDef(
            ColorRGBA.fromRGBA255(120, 120, 125, 255),   // diffuse
            ColorRGBA.fromRGBA255(245, 245, 250, 255),   // specular
            128f,                                         // shininess (max, mirror-like)
            false,
            "Cube"
        ),

        /* 10 – Glass: near-clear, low diffuse alpha, sharp specular */
        new BlockDef(
            ColorRGBA.fromRGBA255(210, 230, 235,  35),   // diffuse (alpha ~14% — mostly transparent)
            ColorRGBA.fromRGBA255(255, 255, 255, 200),   // specular
            96f,                                          // shininess
            false,
            "Py"
        ),

        // ── NEW BLOCKS ────────────────────────────────────────────────────

        /* 11 – Plant: healthy green bush in a pan (connects sideways) */
        new BlockDef(
            ColorRGBA.fromRGBA255( 40, 140,  55, 255),   // leafy green
            ColorRGBA.fromRGBA255( 20,  70,  25, 255),
            18f,
            false,
            "plant_pod"
        ),

        /* 12 – Alien Plant: eerie purple/violet plant pod */
        new BlockDef(
            ColorRGBA.fromRGBA255(140,  50, 180, 255),   // alien purple
            ColorRGBA.fromRGBA255( 80,  20, 120, 255),
            40f,
            false,
            "plant_pod"
        ),

        /* 13 – Wooden Stool: warm brown wood */
        new BlockDef(
            ColorRGBA.fromRGBA255(130,  85,  45, 255),   // oak-ish brown
            ColorRGBA.fromRGBA255( 60,  35,  15, 255),
            12f,
            false,
            "stool"
        ),

        /* 14 – Metal Stool: polished steel look */
        new BlockDef(
            ColorRGBA.fromRGBA255(160, 165, 170, 255),
            ColorRGBA.fromRGBA255(230, 235, 240, 255),
            110f,
            false,
            "stool"
        ),

        /* 15 – Wooden Flat: thin wooden plank / table top */
        new BlockDef(
            ColorRGBA.fromRGBA255(140,  95,  50, 255),
            ColorRGBA.fromRGBA255( 70,  40,  20, 255),
            14f,
            false,
            "CubeFlat"
        ),

        /* 16 – Wooden Tall: wooden post / pillar */
        new BlockDef(
            ColorRGBA.fromRGBA255(120,  75,  40, 255),
            ColorRGBA.fromRGBA255( 55,  30,  12, 255),
            10f,
            false,
            "CubeTall"
        ),

        /* 17 – Metal Flat: thin metal plate */
        new BlockDef(
            ColorRGBA.fromRGBA255(150, 155, 160, 255),
            ColorRGBA.fromRGBA255(220, 225, 230, 255),
            100f,
            false,
            "CubeFlat"
        ),

        /* 18 – Metal Tall: metal post */
        new BlockDef(
            ColorRGBA.fromRGBA255(140, 145, 150, 255),
            ColorRGBA.fromRGBA255(210, 215, 220, 255),
            105f,
            false,
            "CubeTall"
        ),

        /* 19 – Plastic Cube: pure white plastic (full cube) */
        new BlockDef(
            ColorRGBA.fromRGBA255(245, 245, 245, 255),
            ColorRGBA.fromRGBA255(255, 255, 255, 255),
            48f,
            false,
            "Cube"
        ),

        /* 20 – Plastic Tiny: small white plastic cube */
        new BlockDef(
            ColorRGBA.fromRGBA255(245, 245, 245, 255),
            ColorRGBA.fromRGBA255(255, 255, 255, 255),
            48f,
            false,
            "CubeTiny"
        ),

        /* 21 – Plastic Tall: white plastic pillar */
        new BlockDef(
            ColorRGBA.fromRGBA255(245, 245, 245, 255),
            ColorRGBA.fromRGBA255(255, 255, 255, 255),
            48f,
            false,
            "CubeTall"
        ),

        /* 22 – Plastic Flat: white plastic tile / plate */
        new BlockDef(
            ColorRGBA.fromRGBA255(245, 245, 245, 255),
            ColorRGBA.fromRGBA255(255, 255, 255, 255),
            48f,
            false,
            "CubeFlat"
        ),

        /* 23 – Plastic Stool: white plastic stool */
        new BlockDef(
            ColorRGBA.fromRGBA255(245, 245, 245, 255),
            ColorRGBA.fromRGBA255(255, 255, 255, 255),
            48f,
            false,
            "stool"
        ),

        /* 24 – Plastic Plant: white plastic plant pod */
        new BlockDef(
            ColorRGBA.fromRGBA255(245, 245, 245, 255),
            ColorRGBA.fromRGBA255(255, 255, 255, 255),
            48f,
            false,
            "plant_pod"
        ),

        /* 25 – Plastic Py: white plastic PyBall mesh */
        new BlockDef(
            ColorRGBA.fromRGBA255(245, 245, 245, 255),
            ColorRGBA.fromRGBA255(255, 255, 255, 255),
            48f,
            false,
            "Py"
        ),

        /* 26 – Wooden Tiny: small wooden block */
        new BlockDef(
            ColorRGBA.fromRGBA255(135,  90,  48, 255),
            ColorRGBA.fromRGBA255( 65,  38,  18, 255),
            12f,
            false,
            "CubeTiny"
        ),

        /* 27 – Metal Tiny: small metal cube */
        new BlockDef(
            ColorRGBA.fromRGBA255(155, 160, 165, 255),
            ColorRGBA.fromRGBA255(235, 240, 245, 255),
            115f,
            false,
            "CubeTiny"
        ),

        /* 28 – Cushion Flat: soft padded flat surface (matte pastel) */
        new BlockDef(
            ColorRGBA.fromRGBA255(220, 180, 190, 255),   // soft rose
            ColorRGBA.fromRGBA255( 80,  50,  60, 255),
            4f,
            false,
            "CubeFlat"
        ),

        /* 29 – Crystal Tall: tall crystalline growth */
        new BlockDef(
            ColorRGBA.fromRGBA255(160, 210, 255, 255),
            ColorRGBA.fromRGBA255(220, 240, 255, 255),
            120f,
            false,
            "CubeTall"
        ),
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
