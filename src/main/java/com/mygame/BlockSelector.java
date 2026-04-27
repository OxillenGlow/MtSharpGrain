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

        public Object[] getBlockCoords() {
        boolean leftPressed = mouseListener.leftPressed;
        boolean rightPressed = mouseListener.rightPressed;

        System.out.println("Left pressed: " + leftPressed); // Debugging output
        System.out.println("Right pressed: " + rightPressed); // Debugging output

        if (!leftPressed && !rightPressed) {
            return null;
        }

        // Create ray from camera
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        CollisionResults results = new CollisionResults();
        rootNode.collideWith(ray, results);

        if (results.size() == 0) {
            System.out.println("No collision detected!"); // Debugging output
                return null;
        }

        CollisionResult closest = results.getClosestCollision();
        Vector3f hitPoint = closest.getContactPoint();
        System.out.println("Hit point: " + hitPoint); // Debugging output

        // Direction from cam to hit
        Vector3f direction = hitPoint.subtract(cam.getLocation()).normalize();

        Vector3f adjustedPoint;
        boolean isLeft;
        if (leftPressed) {
            adjustedPoint = hitPoint.add(direction.mult(0.3f));
            isLeft = true;
        } else {
            adjustedPoint = hitPoint.subtract(direction.mult(0.3f));
            isLeft = false;
        }
    
        int x = (int) Math.floor(adjustedPoint.x);
        int y = (int) Math.floor(adjustedPoint.y);
        int z = (int) Math.floor(adjustedPoint.z);

        System.out.println("Block coordinates: " + x + ", " + y + ", " + z); // Debugging output
        return new Object[]{x, y, z, isLeft};
    }
}