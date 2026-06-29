package com.mtsharpgrain;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.mtsharpgrain.node.BlockRegistry;
import com.mtsharpgrain.node.BlockRegistry.BlockDef;
import jme3tools.optimize.GeometryBatchFactory;

public class ChunkMeshBuilder {

    /**
     * Fallback colours used when a block ID has no entry in {@link BlockRegistry}.
     * Makes missing blocks very obvious (bright magenta) so they are easy to spot.
     */
    private static final ColorRGBA FALLBACK_DIFFUSE  = ColorRGBA.Magenta;
    private static final ColorRGBA FALLBACK_SPECULAR = ColorRGBA.Black;
    private static final float     FALLBACK_SHININESS = 0f;

    public static Spatial build(ChunkPos pos, BufferedChunk chunk, AssetManager assetManager) {
        int X = pos.getX();
        int Y = pos.getY();
        int Z = pos.getZ();
        String cnkName = "Ck" + X + "y" + Y + "z" + Z;

        Node tempNode = new Node(cnkName);

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    int block = chunk.get(x, y, z);

                    // Skip air / reserved IDs
                    if (BlockRegistry.isAir(block)) continue;

                    // Face-culling: only emit faces adjacent to transparent space
                    boolean px = isAir(chunk, x + 1, y, z);
                    boolean nx = isAir(chunk, x - 1, y, z);
                    boolean py = isAir(chunk, x, y + 1, z);
                    boolean ny = isAir(chunk, x, y - 1, z);
                    boolean pz = isAir(chunk, x, y, z + 1);
                    boolean nz = isAir(chunk, x, y, z - 1);

                    BlockDef def = BlockRegistry.get(block);
                    String meshBuilder = (def != null) ? def.meshBuilder() : "Py";

                    Geometry geo = buildGeometry(meshBuilder, x, y, z, px, py, pz, nx, ny, nz);

                    geo.setMaterial(buildMaterial(assetManager, block));
                    geo.setLocalTranslation(x + 16 * X, y + 16 * Y, z + 16 * Z);
                    tempNode.attachChild(geo);
                }
            }
        }

        Spatial batched = GeometryBatchFactory.optimize(tempNode);
        batched.setName(cnkName);
        return batched;
    }

    // ── Geometry helper ─────────────────────────────────────────────────────

    /**
     * Builds the {@link Geometry} for one block according to its
     * {@code meshBuilder} type. "Py" keeps the original face-culled
     * PyBall mesh; the "Cube*" variants build a simple {@link Box}.
     *
     * Box geometries are centered in their 1x1x1 grid cell via the local
     * translation offset baked into the returned geometry's name-relative
     * position; CubeTall/CubeFlat are floor-aligned (sit on the cell's
     * bottom face) rather than vertically centered.
     */
    private static Geometry buildGeometry(String meshBuilder, int x, int y, int z,
                                           boolean px, boolean py, boolean pz,
                                           boolean nx, boolean ny, boolean nz) {
        switch (meshBuilder) {
            case "Cube": {
                Box boxMesh = new Box(0.5f, 0.5f, 0.5f);
                Geometry boxGeo = new Geometry("Colored Box", boxMesh);
                boxGeo.setLocalTranslation(0.5f, 0.5f, 0.5f); // center in cell
                return boxGeo;
            }
            case "CubeTiny": {
                Box boxMesh = new Box(0.2f, 0.2f, 0.2f);
                Geometry boxGeo = new Geometry("Colored Box", boxMesh);
                boxGeo.setLocalTranslation(0.5f, 0.5f, 0.5f); // center in cell
                return boxGeo;
            }
            case "CubeTall": {
                Box boxMesh = new Box(0.3f, 0.5f, 0.3f);
                Geometry boxGeo = new Geometry("Colored Box", boxMesh);
                // floor-aligned: half-height above the cell's bottom face
                boxGeo.setLocalTranslation(0.5f, 0.5f, 0.5f);
                return boxGeo;
            }
            case "CubeFlat": {
                Box boxMesh = new Box(0.5f, 0.1f, 0.5f);
                Geometry boxGeo = new Geometry("Colored Box", boxMesh);
                // floor-aligned: half-height above the cell's bottom face
                boxGeo.setLocalTranslation(0.5f, 0.1f, 0.5f);
                return boxGeo;
            }
            case "Py":
            default: {
                Mesh mesh = PyBallJmeMesh.getMesh(!px, !py, !pz, !nx, !ny, !nz, false);
                return new Geometry("Geo" + x + y + z, mesh);
            }
        }
    }

    // ── Material helper ────────────────────────────────────────────────────

    /**
     * Builds a {@code Lighting.j3md} material for the given block ID.
     * Ambient is always taken from {@link BlockRegistry#AMBIENT}.
     * Diffuse, specular, and shininess come from the block's {@link BlockDef};
     * if none exists the fallback (magenta) is used so missing blocks are obvious.
     */
    private static Material buildMaterial(AssetManager assetManager, int blockId) {
        BlockDef def = BlockRegistry.get(blockId);

        ColorRGBA diffuse;
        ColorRGBA specular;
        float     shininess;

        if (def != null) {
            diffuse   = def.diffuse();
            specular  = def.specular();
            shininess = def.shininess();
        } else {
            diffuse   = FALLBACK_DIFFUSE;
            specular  = FALLBACK_SPECULAR;
            shininess = FALLBACK_SHININESS;
        }

        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient",   BlockRegistry.AMBIENT);
        mat.setColor("Diffuse",   diffuse);
        mat.setColor("Specular",  specular);
        mat.setFloat("Shininess", shininess);
        return mat;
    }

    // ── Face-visibility helper ─────────────────────────────────────────────

    /**
     * Returns true when the neighbouring position is transparent,
     * meaning the face between the two blocks should be rendered.
     * Out-of-bounds positions (chunk edges) always return false.
     */
    private static boolean isAir(BufferedChunk chunk, int x, int y, int z) {
        if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
            return false;
        }
        return BlockRegistry.isAir(chunk.get(x, y, z));
    }
}
