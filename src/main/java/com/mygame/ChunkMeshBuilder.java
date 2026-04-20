package com.mygame;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import jme3tools.optimize.GeometryBatchFactory;

public class ChunkMeshBuilder {

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
                    if (block == 0 || block == 1) continue;

                    // Check adjacent blocks to determine which faces to render
                    boolean px = isAir(chunk, x + 1, y, z); // positive x
                    boolean nx = isAir(chunk, x - 1, y, z); // negative x
                    boolean py = isAir(chunk, x, y + 1, z); // positive y
                    boolean ny = isAir(chunk, x, y - 1, z); // negative y
                    boolean pz = isAir(chunk, x, y, z + 1); // positive z
                    boolean nz = isAir(chunk, x, y, z - 1); // negative z

                    Mesh mesh = PyBallJmeMesh.getMesh(px, py, pz, nx, ny, nz);
                    Geometry geo = new Geometry("Geo" + x + y + z, mesh);
                    
                    Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
                    mat.setBoolean("UseMaterialColors", true);
                    mat.setColor("Ambient", ColorRGBA.Green);
                    mat.setColor("Diffuse", ColorRGBA.Green);
                    geo.setMaterial(mat);

                    // Move relative to world
                    geo.setLocalTranslation(x + 16 * X, y + 16 * Y, z + 16 * Z);
                    tempNode.attachChild(geo);
                }
            }
        }

        // Batching is CPU intensive, perfect for a background thread
        Spatial batched = GeometryBatchFactory.optimize(tempNode);
        batched.setName(cnkName);
        return batched;
    }

    /**
     * Checks if the block at the given position is air (0 or 1).
     * If the position is out of bounds (at chunk edge), returns true, so faces render.
     */
    private static boolean isAir(BufferedChunk chunk, int x, int y, int z) {
        // If out of bounds, default to true (render the face)
        if (x < 0 || x >= 16 || y < 0 || y >= 16 || z < 0 || z >= 16) {
            return true;
        }
        
        int blockId = chunk.get(x, y, z);
        // Return true if the adjacent block is air (0 or 1)
        return blockId == 0 || blockId == 1;
    }
}

