package com.mtsharpgrain.newton;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.collision.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import java.util.HashMap;
import java.util.Map;

public class PhysicsControl extends AbstractControl {
    public Map<String, Vector3f> forceMap = new HashMap<>();
    public Vector3f velocity = new Vector3f(0, 0, 0);
    private float pushConstant = 0.01f;
    private Node worldNode; // Reference to the world root node for raycasting

    public PhysicsControl(Node worldNode) {
        this.worldNode = worldNode;
    }

    @Override
    protected void controlUpdate(float tpf) {
        // Apply all forces to velocity
        Vector3f totalForce = new Vector3f(0, 0, 0);
        for (Vector3f force : forceMap.values()) {
            totalForce.addLocal(force);
        }

        float dragCoefficient = 0.1f; // Adjust this value to tweak the drag strength
        if (velocity.lengthSquared() > 0.5f) { // Avoid tiny values
            Vector3f dragForce = velocity.mult(-dragCoefficient * velocity.length());
            velocity.addLocal(dragForce.multLocal(tpf));
        }

        // Update velocity
        velocity.addLocal(totalForce.multLocal(tpf));

        // Calculate movement for this tick
        Vector3f movement = velocity.mult(tpf);
        Vector3f newLocation = spatial.getLocalTranslation().add(movement);

        // Check for collisions in the new location
        Vector3f collisionNormal = detectCollision(spatial.getLocalTranslation(), newLocation);
        if (collisionNormal != null) {
            collisionNormal.normalizeLocal();
            float dot = velocity.dot(collisionNormal);
            if (dot < 0) {
                // Zero out the normal component of velocity
                Vector3f projection = collisionNormal.mult(dot);
                velocity.subtractLocal(projection);
                // Apply small push away from the surface
                newLocation.addLocal(collisionNormal.multLocal(pushConstant));
            }
        }

        // Update spatial location
        spatial.setLocalTranslation(newLocation);
    }

    private Vector3f detectCollision(Vector3f from, Vector3f to) {
        Vector3f direction = to.subtract(from).normalizeLocal();
        Ray ray = new Ray(from, direction);
        CollisionResults results = new CollisionResults();
        worldNode.collideWith(ray, results);

        if (results.size() > 0) {
            CollisionResult closest = results.getClosestCollision();
            if (closest.getDistance() < to.distance(from)) {
                return closest.getContactNormal();
            }
        }
        return null;
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
}
