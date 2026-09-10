package com.mtsharpgrain.newton;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.collision.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.CameraControl;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple force/velocity physics controller for the camera/player.
 *
 * The Camera is the authoritative position. This is intentional because
 * other systems in Main can move the camera directly (teleports, world
 * movement, scripts, etc.). The attached Spatial is only the host for this
 * control and is never used as the source of the physics position.
 *
 * Collision tests use the camera's WORLD position, so this also works with
 * Main's floating-origin rootNode translation.
 */
public class PhysicsControl extends AbstractControl {
    public final Map<String, Vector3f> forceMap = new HashMap<>();
    public final Vector3f velocity = new Vector3f();

    private static final float DRAG_COEFFICIENT = 0.1f;
    private static final float PUSH_CONSTANT = 0.01f;
    private static final float MIN_MOVEMENT = 0.000001f;

    private final Node worldNode;
    private Camera camera;

    public PhysicsControl(Node worldNode) {
        if (worldNode == null) {
            throw new IllegalArgumentException("worldNode cannot be null");
        }
        this.worldNode = worldNode;
    }

    /**
     * Registers the camera whose position is used by this physics controller.
     * The camera remains the authoritative position even when other game
     * systems move it between physics updates.
     */
    public void registerCamera(Camera camera) {
        this.camera = camera;
    }

    /**
     * Returns the absolute game-world position of the camera, independent of
     * rootNode's floating-origin translation.
     */
    public Vector3f getWorldPosition() {
        if (camera == null) {
            return new Vector3f();
        }
        return camera.getLocation().subtract(worldNode.getWorldTranslation());
    }

    /**
     * CameraNode installs a CameraControl for SpatialToCamera. That control
     * would make the node overwrite the camera every frame, which conflicts
     * with the camera being the authoritative position for this controller.
     * Disable only that position-sync path; FlyByCamera can still own the
     * camera rotation.
     */
    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);

        if (spatial instanceof Node node) {
            CameraControl cameraControl = node.getControl(CameraControl.class);
            if (cameraControl != null) {
                cameraControl.setEnabled(false);
            }
        }
    }

    @Override
    protected void controlUpdate(float tpf) {
        // Physics is camera-driven. Never fall back to spatial position.
        if (camera == null || tpf <= 0f) {
            return;
        }

        // Read the camera at the start of every physics update. This picks up
        // movement performed by Main, scripts, teleports, etc. since the last
        // physics tick.
        Vector3f cameraPosition = camera.getLocation().clone();

        Vector3f totalForce = new Vector3f();
        for (Vector3f force : forceMap.values()) {
            if (force != null) {
                totalForce.addLocal(force);
            }
        }

        float speedSquared = velocity.lengthSquared();
        if (speedSquared > 0.5f) {
            float speed = (float) Math.sqrt(speedSquared);
            velocity.addLocal(velocity.mult(-DRAG_COEFFICIENT * speed * tpf));
        }

        velocity.addLocal(totalForce.mult(tpf));

        Vector3f movement = velocity.mult(tpf);
        float distance = movement.length();
        if (distance <= MIN_MOVEMENT) {
            return;
        }

        Vector3f newCameraPosition = cameraPosition.add(movement);
        Vector3f collisionNormal = detectCollision(cameraPosition, newCameraPosition, distance);

        if (collisionNormal != null) {
            collisionNormal.normalizeLocal();

            float normalVelocity = velocity.dot(collisionNormal);
            if (normalVelocity < 0f) {
                // Remove only the velocity component directed into the surface.
                velocity.subtractLocal(collisionNormal.mult(normalVelocity));
                newCameraPosition.addLocal(collisionNormal.mult(PUSH_CONSTANT));
            }
        }

        // Physics is the only system that changes the camera here. External
        // systems remain free to move the camera, and their new position will
        // be picked up at the next physics update.
        camera.setLocation(newCameraPosition);
    }

    private Vector3f detectCollision(Vector3f from, Vector3f to, float distance) {
        Vector3f direction = to.subtract(from);
        if (direction.lengthSquared() <= MIN_MOVEMENT * MIN_MOVEMENT) {
            return null;
        }

        direction.normalizeLocal();

        Ray ray = new Ray(from, direction);
        CollisionResults results = new CollisionResults();
        worldNode.collideWith(ray, results);

        if (results.size() == 0) {
            return null;
        }

        CollisionResult closest = results.getClosestCollision();
        if (closest != null
                && closest.getDistance() > 0f
                && closest.getDistance() <= distance) {
            return closest.getContactNormal();
        }

        return null;
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm,
                                 com.jme3.renderer.ViewPort vp) {
    }
}
