package com.mtsharpgrain;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.jme3.scene.shape.Box;
import java.util.ArrayList;
import java.util.List;

public class PyBallJmeMesh {

    private static final Mesh[] meshArray = new Mesh[64];
    private static final Mesh EMPTY_MESH = new Mesh();
    /**
     * Initializes the mesh cache for all 64 possible face combinations.
     */
    public static void init() {
        for (int i = 0; i < 64; i++) {

            boolean[] faces = new boolean[6];

            for (int j = 0; j < 6; j++) {
                faces[j] = (i & (1 << j)) != 0;
            }

            meshArray[i] = buildMesh(faces);
        }
    }

    public static Mesh getMesh(
            boolean px,
            boolean py,
            boolean pz,
            boolean nx,
            boolean ny,
            boolean nz,
            boolean exception
    ) {

        int total =
                (px ? 1 : 0) +
                (py ? 1 : 0) +
                (pz ? 1 : 0) +
                (nx ? 1 : 0) +
                (ny ? 1 : 0) +
                (nz ? 1 : 0);
        int index = 0;
        // Completely hidden
        if (total < 4) {
            if (px) index |= 1;
            if (py) index |= (1 << 1);
            if (pz) index |= (1 << 2);
            if (nx) index |= (1 << 3);
            if (ny) index |= (1 << 4);
            if (nz) index |= (1 << 5);
            return meshArray[index];
        } else {return new Box(0.5f,0.5f,0.5f); 
        }

    }

    private static Mesh buildMesh(boolean[] faces) {

        int total =
                (faces[0] ? 1 : 0) +
                (faces[1] ? 1 : 0) +
                (faces[2] ? 1 : 0) +
                (faces[3] ? 1 : 0) +
                (faces[4] ? 1 : 0) +
                (faces[5] ? 1 : 0);

        // Fully hidden
        if (total == 6) {
            return EMPTY_MESH;
        }

        List<Vector3f> verts = new ArrayList<>();
        List<Vector3f> norms = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // nx
        if (faces[3]) {
            addPyramidNoBase(
                    verts, norms, indices,
                    new Vector3f(0.5f,0.5f,0.5f),
                    new Vector3f(0.5f,0.5f,-0.5f),
                    new Vector3f(0.5f,-0.5f,-0.5f),
                    new Vector3f(0.5f,-0.5f,0.5f)
            );

            
        }

        // px
        if (faces[0]) {
            addPyramidNoBase(
                    verts, norms, indices,
                    new Vector3f(-0.5f,0.5f,-0.5f),
                    new Vector3f(-0.5f,0.5f,0.5f),
                    new Vector3f(-0.5f,-0.5f,0.5f),
                    new Vector3f(-0.5f,-0.5f,-0.5f)
            );

            
        }

        // ny
        if (faces[4]) {
            addPyramidNoBase(
                    verts, norms, indices,
                    new Vector3f(-0.5f,0.5f,0.5f),
                    new Vector3f(0.5f,0.5f,0.5f),
                    new Vector3f(0.5f,0.5f,-0.5f),
                    new Vector3f(-0.5f,0.5f,-0.5f)
            );

            
        }

        // py
        if (faces[1]) {
            addPyramidNoBase(
                    verts, norms, indices,
                    new Vector3f(-0.5f,-0.5f,-0.5f),
                    new Vector3f(0.5f,-0.5f,-0.5f),
                    new Vector3f(0.5f,-0.5f,0.5f),
                    new Vector3f(-0.5f,-0.5f,0.5f)
            );

            
        }

        // nz
        if (faces[5]) {
            addPyramidNoBase(
                    verts, norms, indices,
                    new Vector3f(-0.5f,0.5f,0.5f),
                    new Vector3f(-0.5f,-0.5f,0.5f),
                    new Vector3f(0.5f,-0.5f,0.5f),
                    new Vector3f(0.5f,0.5f,0.5f)
            );

            
        }

        // pz
        if (faces[2]) {
            addPyramidNoBase(
                    verts, norms, indices,
                    new Vector3f(0.5f,0.5f,-0.5f),
                    new Vector3f(0.5f,-0.5f,-0.5f),
                    new Vector3f(-0.5f,-0.5f,-0.5f),
                    new Vector3f(-0.5f,0.5f,-0.5f)
            );

            addQuad(
                    verts, norms, indices,
                    new Vector3f(0.5f,0.5f,-0.5f),
                    new Vector3f(-0.5f,0.5f,-0.5f),
                    new Vector3f(-0.5f,-0.5f,-0.5f),
                    new Vector3f(0.5f,-0.5f,-0.5f)
            );
        }

        Mesh mesh = new Mesh();

        mesh.setBuffer(
                VertexBuffer.Type.Position,
                3,
                BufferUtils.createFloatBuffer(verts.toArray(new Vector3f[0]))
        );

        mesh.setBuffer(
                VertexBuffer.Type.Normal,
                3,
                BufferUtils.createFloatBuffer(norms.toArray(new Vector3f[0]))
        );

        int[] ind = new int[indices.size()];

        for (int i = 0; i < ind.length; i++) {
            ind[i] = indices.get(i);
        }

        mesh.setBuffer(
                VertexBuffer.Type.Index,
                3,
                BufferUtils.createIntBuffer(ind)
        );

        mesh.updateBound();

        return mesh;
    }

    private static void addPyramidNoBase(
            List<Vector3f> verts,
            List<Vector3f> norms,
            List<Integer> indices,
            Vector3f b1,
            Vector3f b2,
            Vector3f b3,
            Vector3f b4
    ) {

        Vector3f tip = new Vector3f(0, 0, 0);

        addFace(verts, norms, indices, tip, b1, b2);
        addFace(verts, norms, indices, tip, b2, b3);
        addFace(verts, norms, indices, tip, b3, b4);
        addFace(verts, norms, indices, tip, b4, b1);
    }

    private static void addQuad(
            List<Vector3f> verts,
            List<Vector3f> norms,
            List<Integer> indices,
            Vector3f v1,
            Vector3f v2,
            Vector3f v3,
            Vector3f v4
    ) {

        addFace(verts, norms, indices, v1, v2, v3);
        addFace(verts, norms, indices, v1, v3, v4);
    }

    private static void addFace(
            List<Vector3f> verts,
            List<Vector3f> norms,
            List<Integer> indices,
            Vector3f v1,
            Vector3f v2,
            Vector3f v3
    ) {

        int offset = verts.size();

        Vector3f normal =
                v2.subtract(v1)
                        .crossLocal(v3.subtract(v1))
                        .normalizeLocal();

        verts.add(v1);
        verts.add(v2);
        verts.add(v3);

        norms.add(normal);
        norms.add(normal);
        norms.add(normal);

        indices.add(offset);
        indices.add(offset + 1);
        indices.add(offset + 2);
    }
}
