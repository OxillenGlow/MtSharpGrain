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
 * Simple force/velocity physics controller for a Spatial.
 *
 * The spatial's local translation is kept as the absolute physics position.
 * Collision tests use the spatial's WORLD translation, which is important
 * when Main uses rootNode as a floating-origin offset.
 */
public class PhysicsControl extends AbstractControl {
    public final Map<String, Vector3f> forceMap = new HashMap<>();
    public final Vector3f velocity = new Vector3f();

    private static final float DRAG_COEFFICIENT = 0.1f;
    private static final float PUSH_CONSTANT = 0.01f;
    private static final float MIN_MOVEMENT = 0.000001f;

    private final Node worldNode;
    private Camera camera;
    private boolean initialized;

    public PhysicsControl(Node worldNode) {
        if (worldNode == null) {
            throw new IllegalArgumentException("worldNode cannot be null");
        }
        this.worldNode = worldNode;
    }

    /**
     * Registers the camera controlled by this physics object.
     *
     * Main currently sets the camera spawn position after registering the
     * control, so the first physics update copies that camera position into
     * the CameraNode rather than snapping it to (0,0,0).
     */
    public void registerCamera(Camera camera) {
        this.camera = camera;
    }

    /**
     * Returns the absolute game-world position, independent of rootNode's
     * floating-origin translation.
     */
    public Vector3f getWorldPosition() {
        if (spatial == null) {
            return new Vector3f();
        }
        return spatial.getWorldTranslation().subtract(worldNode.getWorldTranslation());
    }

    /**
     * CameraNode installs a CameraControl before this PhysicsControl.
     * SpatialToCamera would otherwise overwrite Main's camera spawn position
     * before physics gets a chance to initialize the spatial. PhysicsControl
     * therefore owns the camera position once it is attached.
     *
     * Camera rotation remains owned by FlyCamPhysicsControl/input handling.
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
        if (spatial == null || tpf <= 0f) {
            return;
        }

        // Main calls cam.setLocation(spawn) after the CameraNode/control is
        // created. Copy that initial render-space position into the spatial
        // before applying any physics.
        if (!initialized) {
            if (camera != null) {
                Vector3f cameraWorld = camera.getLocation();
                Vector3f origin = worldNode.getWorldTranslation();
                spatial.setLocalTranslation(cameraWorld.subtract(origin));
            }
            initialized = true;
        }

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
            syncCamera();
            return;
        }

        // Local translation is the absolute physics coordinate. World
        // translation includes rootNode's floating-origin offset and is the
        // coordinate space used by Node.collideWith().
        Vector3f oldLocal = spatial.getLocalTranslation().clone();
        Vector3f oldWorld = spatial.getWorldTranslation();
        Vector3f newLocal = oldLocal.add(movement);
        Vector3f newWorld = oldWorld.add(movement);

        Vector3f collisionNormal = detectCollision(oldWorld, newWorld, distance);
        if (collisionNormal != null) {
            collisionNormal.normalizeLocal();

            float normalVelocity = velocity.dot(collisionNormal);
            if (normalVelocity < 0f) {
                // Remove only the velocity component directed into the surface.
                velocity.subtractLocal(collisionNormal.mult(normalVelocity));
                newLocal.addLocal(collisionNormal.mult(PUSH_CONSTANT));
            }
        }

        spatial.setLocalTranslation(newLocal);
        syncCamera();
    }

    private void syncCamera() {
        if (camera != null && spatial != null) {
            camera.setLocation(spatial.getWorldTranslation());
        }
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
