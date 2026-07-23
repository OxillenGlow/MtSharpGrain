package com.mtsharpgrain;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class BlockSelector {

    // Chunk spatials are named "Ck<X>y<Y>z<Z>" (e.g. "Ck0y0z0", "Ck-1y2z-3").
    // This regex identifies them purely by name — no userdata needed.
    private static final java.util.regex.Pattern CHUNK_NAME =
            java.util.regex.Pattern.compile("Ck-?\\d+y-?\\d+z-?\\d+");

    private final com.jme3.renderer.Camera cam;
    private final Node rootNode;

    public BlockSelector(com.jme3.renderer.Camera cam, Node rootNode) {
        this.cam = cam;
        this.rootNode = rootNode;
    }

    // ── Raycasting ───────────────────────────────────────────────────────────

    /**
     * Fires a ray from the camera into the scene and returns the closest
     * collision, or {@code null} if nothing was hit.
     */
    public CollisionResult raycast() {
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        CollisionResults results = new CollisionResults();
        rootNode.collideWith(ray, results);
        return results.size() == 0 ? null : results.getClosestCollision();
    }

    // ── Chunk detection — name only, no userdata ──────────────────────────

    /**
     * Returns {@code true} when the hit belongs to a batched chunk mesh.
     * Walks up the spatial hierarchy from the hit geometry to handle the case
     * where {@link jme3tools.optimize.GeometryBatchFactory} produces a Node
     * with multiple child geometries (one per material). The chunk name is
     * always set on the outermost spatial returned by the builder.
     */
    public static boolean isChunkHit(CollisionResult hit) {
        Spatial s = hit.getGeometry();
        while (s != null) {
            if (isChunkName(s.getName())) return true;
            s = s.getParent();
        }
        return false;
    }

    /**
     * Walks up from the hit geometry and returns the first spatial name that
     * is not a chunk name and not null/empty. This is the name the mod (or
     * game code) gave to its spatial via {@code Scene.createCube(name, ...)}
     * or {@code Scene.createNode(name)}.
     *
     * <p>Returns {@code null} only if every ancestor is unnamed — which should
     * not happen for mod-created spatials.
     */
    public static String resolveHitName(CollisionResult hit) {
        Spatial s = hit.getGeometry();
        while (s != null) {
            String name = s.getName();
            if (name != null && !name.isEmpty() && !isChunkName(name)) {
                return name;
            }
            s = s.getParent();
        }
        return null;
    }

    private static boolean isChunkName(String name) {
        return name != null && CHUNK_NAME.matcher(name).matches();
    }

    // ── Block coordinate computation ─────────────────────────────────────

    /**
     * Given a collision that has already been confirmed as a chunk hit, compute
     * the integer block coordinates that should be placed-into or removed-from.
     *
     * @param hit         the confirmed chunk collision
     * @param leftPressed {@code true} = place (offset along normal),
     *                    {@code false} = remove (offset into surface)
     */
    public BlockSelection selectionFrom(CollisionResult hit, boolean leftPressed) {
        Vector3f hitPoint = hit.getContactPoint();
        Vector3f normal   = hit.getContactNormal();
        Vector3f adjusted = new Vector3f(hitPoint);

        if (leftPressed) {
            adjusted.addLocal(normal.mult(0.6f));
        } else {
            adjusted.addLocal(normal.mult(-0.01f));
        }

        adjusted.subtractLocal(rootNode.getLocalTranslation());

        int x = (int) Math.floor(adjusted.x + 0.5);
        int y = (int) Math.floor(adjusted.y + 0.5);
        int z = (int) Math.floor(adjusted.z + 0.5);
        return new BlockSelection(x, y, z, leftPressed);
    }

    // ── Legacy convenience wrapper ────────────────────────────────────────

    /**
     * Original API — still works. Returns the block selection if a chunk was
     * hit, {@code null} otherwise (including when a non-chunk spatial is hit).
     *
     * Prefer calling {@link #raycast()} + {@link #isChunkHit} + {@link #selectionFrom}
     * directly when you also need to handle non-chunk clicks.
     */
    public BlockSelection getSelection(boolean leftPressed) {
        CollisionResult hit = raycast();
        if (hit == null || !isChunkHit(hit)) return null;
        return selectionFrom(hit, leftPressed);
    }
}
