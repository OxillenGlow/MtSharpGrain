package com.mtsharpgrain;

import com.jme3.collision.CollisionResults;
import com.jme3.collision.CollisionResult;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class BlockSelector {
    private com.jme3.renderer.Camera cam;
    private Node rootNode;

    public BlockSelector(com.jme3.renderer.Camera cam, Node rootNode) {
        this.cam = cam;
        this.rootNode = rootNode;
    }

    public BlockSelection getSelection(boolean leftPressed) {
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        CollisionResults results = new CollisionResults();
        rootNode.collideWith(ray, results);

        if (results.size() == 0) return null;

        CollisionResult closest = results.getClosestCollision();
        Vector3f hitPoint = closest.getContactPoint();
        Vector3f normal = closest.getContactNormal();
        Vector3f adjustedPoint = new Vector3f(hitPoint);

        if (leftPressed) {
            adjustedPoint.addLocal(normal.mult(0.6f));
        } else {
            adjustedPoint.addLocal(normal.mult(-0.01f));
        }

        int x = (int) Math.floor(adjustedPoint.x + 0.5);
        int y = (int) Math.floor(adjustedPoint.y + 0.5);
        int z = (int) Math.floor(adjustedPoint.z + 0.5);
        return new BlockSelection(x, y, z, leftPressed);
    }
}
