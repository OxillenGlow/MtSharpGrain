package com.mtsharpgrain;

import com.jme3.material.RenderState.BlendMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
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
import java.util.ArrayList;
import java.util.List;

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

                    // Face-culling and smoothing: only emit faces adjacent to transparent space and mesh naturally disconnects from air blocks.
                    boolean px = isAir(chunk, x + 1, y, z);
                    boolean nx = isAir(chunk, x - 1, y, z);
                    boolean py = isAir(chunk, x, y + 1, z);
                    boolean ny = isAir(chunk, x, y - 1, z);
                    boolean pz = isAir(chunk, x, y, z + 1);
                    boolean nz = isAir(chunk, x, y, z - 1);

                    if (!px && !nx && !py && !ny && !pz && !nz && !(x == 15) && !(y == 15) && !(z == 15) && !(x == 0) && !(y == 0) && !(z == 0) ) {
                        continue;
                    }

                    BlockDef def = BlockRegistry.get(block);
                    String meshBuilder;
                    if (def != null || block > -1) {
                        meshBuilder = def.meshBuilder();
                    } else {
                        // Check dynamic registry for mod blocks
                        try {
                            var registry = com.mtsharpgrain.node.DynamicBlockRegistry.getInstance();
                            meshBuilder = (registry != null) ? registry.getBuilderFor(block).orElse("Py") : "Py";
                        } catch (Throwable t) {
                            meshBuilder = "Py";
                        }
                    }
                    Geometry geo = buildGeometry(meshBuilder, x, y, z, px, py, pz, nx, ny, nz);

                    Material mat = buildMaterial(assetManager, block);
                    geo.setMaterial(mat);
                    if (isTransparent(block)) {
                        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
                        geo.setQueueBucket(Bucket.Transparent);
                    }
                    geo.setLocalTranslation(x + 16 * X, y + 16 * Y, z + 16 * Z);
                    tempNode.attachChild(geo);
                }
            }
        }

        Spatial batched = GeometryBatchFactory.optimize(tempNode);
        batched.setName(cnkName);
        return batched;
    }

    // —— Geometry helper ————————————————————————————————————————

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
            case "stool": {
                // 4 thin legs + 1 flat seat → merged into one Mesh (same technique used in PyBallJmeMesh)
                final float legHalf = 0.06f;
                final float legHalfH = 0.40f;   // legs go from y=0 to y=0.80
                final float inset   = 0.18f;   // distance from cell edge to leg centre

                // temporary geometries (will be merged, then discarded)
                List<Geometry> parts = new ArrayList<>(5);

                // four corner legs
                float[][] legXZ = {
                    { inset,      inset },
                    { 1f - inset, inset },
                    { inset,      1f - inset },
                    { 1f - inset, 1f - inset }
                };
                for (int i = 0; i < 4; i++) {
                    Box legMesh = new Box(legHalf, legHalfH, legHalf);
                    Geometry leg = new Geometry("leg" + i, legMesh);
                    leg.setLocalTranslation(legXZ[i][0], legHalfH, legXZ[i][1]);
                    parts.add(leg);
                }
     
                // flat seat sitting on top of the legs
                Box seatMesh = new Box(0.42f, 0.05f, 0.42f);
                Geometry seat = new Geometry("seat", seatMesh);
                seat.setLocalTranslation(0.5f, 0.80f + 0.05f, 0.5f);
                parts.add(seat);

                // merge exactly like PyBallJmeMesh does
                Mesh merged = new Mesh();
                GeometryBatchFactory.mergeGeometries(parts, merged);

                Geometry stoolGeo = new Geometry("Stool", merged);
                // no extra local translation needed – the parts already sit correctly inside the cell
                return stoolGeo;
            }
            case "plant_pod": {
                List<Geometry> parts = new ArrayList<>(2);
                Box panMesh = new Box(0.48f, 0.06f, 0.48f);
                Geometry pan = new Geometry("PlantPan", panMesh);
                pan.setLocalTranslation(0.5f, 0.06f, 0.5f);   // sits on the floor
                parts.add(pan);

                float minX = 0.05f, maxX = 0.95f;
                float minZ = 0.05f, maxZ = 0.95f;
                if (!px) maxX = 1.00f;   // solid neighbour on +X → reach the edge
                if (!nx) minX = 0.00f;   // solid neighbour on -X
                if (!pz) maxZ = 1.00f;   // solid neighbour on +Z
                if (!nz) minZ = 0.00f;   // solid neighbour on -Z

                float halfX = (maxX - minX) * 0.5f;
                float halfZ = (maxZ - minZ) * 0.5f;
                float centerX = (minX + maxX) * 0.5f;
                float centerZ = (minZ + maxZ) * 0.5f;
    
                // 0.9 tall bush sitting just above the pan
                final float bushHalfY = 0.45f;          // total height 0.9
                final float bushCenterY = 0.12f + bushHalfY;  // top of pan ≈ 0.12

                Box bushMesh = new Box(halfX, bushHalfY, halfZ);
                Geometry bush = new Geometry("PlantBush", bushMesh);
                bush.setLocalTranslation(centerX, bushCenterY, centerZ);
                parts.add(bush);

                // Merge exactly like the stool / PyBallJmeMesh
                Mesh merged = new Mesh();
                GeometryBatchFactory.mergeGeometries(parts, merged);

                return new Geometry("PlantPod", merged);
            }
            case "Py":
            default: {
                Mesh mesh = PyBallJmeMesh.getMesh(!px, !py, !pz, !nx, !ny, !nz);
                return new Geometry("Geo" + x + y + z, mesh);
            }
        }
    }

    // —— Material helper ————————————————————————————————————————

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
            // Try dynamic registry for mod-provided colours
            var registry = com.mtsharpgrain.node.DynamicBlockRegistry.getInstance();
            if (registry != null) {
                var opt = registry.getById(blockId);
                if (opt.isPresent()) {
                    var reg = opt.get();
                    diffuse = new ColorRGBA(reg.dr(), reg.dg(), reg.db(), reg.da());
                    specular = new ColorRGBA(reg.sr(), reg.sg(), reg.sb(), reg.sa());
                    shininess = reg.shininess();
                } else {
                    diffuse   = FALLBACK_DIFFUSE;
                    specular  = FALLBACK_SPECULAR;
                    shininess = FALLBACK_SHININESS;
                }
            } else {
                diffuse   = FALLBACK_DIFFUSE;
                specular  = FALLBACK_SPECULAR;
                shininess = FALLBACK_SHININESS;
            }
        }

        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient",   BlockRegistry.AMBIENT);
        mat.setColor("Diffuse",   diffuse);
        mat.setColor("Specular",  specular);
        mat.setFloat("Shininess", shininess);
        return mat;
    }

    // —— Face-visibility helper ————————————————————————————————————

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

    /** @return True if this block's diffuse alpha is less than fully opaque (e.g. Glass). */
    private static boolean isTransparent(int blockId) {
        BlockDef def = BlockRegistry.get(blockId);
        return def != null && def.diffuse().a < 1f;
    }
}
