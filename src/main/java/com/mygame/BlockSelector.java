package com.mygame;

import com.jme3.collision.CollisionResults;
import com.jme3.collision.CollisionResult;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class BlockSelector {
    private com.jme3.renderer.Camera cam;
    private Node rootNode;
    private MouseListener mouseListener;

    public BlockSelector(com.jme3.renderer.Camera cam, Node rootNode, MouseListener mouseListener) {
        this.cam = cam;
        this.rootNode = rootNode;
        this.mouseListener = mouseListener;
    }
        public BlockSelection getSelection() {
        boolean leftPressed = mouseListener.leftPressed;
        boolean rightPressed = mouseListener.rightPressed;

        if (!leftPressed && !rightPressed) return null;

        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        CollisionResults results = new CollisionResults();
        rootNode.collideWith(ray, results);

        if (results.size() == 0) return null;

        CollisionResult closest = results.getClosestCollision();
        Vector3f hitPoint = closest.getContactPoint();
        Vector3f normal = closest.getContactNormal(); // The magic fix
        
        Vector3f adjustedPoint = new Vector3f(hitPoint);

        if (leftPressed) {
            // Shift point SLIGHTLY out of the block face to find the empty space
           adjustedPoint.addLocal(normal.mult(0.4f));
        } else {
            // Shift point SLIGHTLY into the block face to find the block itself
            adjustedPoint.addLocal(normal.mult(-0.01f));
        }

        // Use Math.floor on all axes for consistent grid alignment
        int x = (int) Math.floor(adjustedPoint.x + 0.5);
        int y = (int) Math.floor(adjustedPoint.y + 0.5);
        int z = (int) Math.floor(adjustedPoint.z + 0.5);

        return new BlockSelection(x, y, z, leftPressed);
    }

    
}