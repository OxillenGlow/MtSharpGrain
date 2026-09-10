package com.mtsharpgrain.newton;

import com.jme3.input.CameraInput;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.input.FlyByCamera;

/**
 * FlyByCamera input adapter that turns WASD/vertical movement into forces
 * consumed by PhysicsControl instead of moving the Camera directly.
 */
public class FlyCamPhysicsControl extends FlyByCamera {
    private boolean flyMode = false;
    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private boolean rise;
    private boolean fall;

    private float moveSpeed = 10f;
    private float riseSpeed = 5f;
    private final PhysicsControl physicsControl;

    public FlyCamPhysicsControl(Camera cam, PhysicsControl physicsControl) {
        super(cam);
        this.physicsControl = physicsControl;
    }

    @Override
    public void registerWithInput(InputManager inputManager) {
        // Keep FlyByCamera's mouse-look/zoom mappings, but remove its direct
        // movement mappings. Otherwise FlyByCamera moves cam directly while
        // PhysicsControl also moves the CameraNode.
        super.registerWithInput(inputManager);

        inputManager.deleteMapping(CameraInput.FLYCAM_FORWARD);
        inputManager.deleteMapping(CameraInput.FLYCAM_BACKWARD);
        inputManager.deleteMapping(CameraInput.FLYCAM_STRAFELEFT);
        inputManager.deleteMapping(CameraInput.FLYCAM_STRAFERIGHT);
        inputManager.deleteMapping(CameraInput.FLYCAM_RISE);
        inputManager.deleteMapping(CameraInput.FLYCAM_LOWER);

        inputManager.addMapping("physics-fly", new KeyTrigger(KeyInput.KEY_F));
        inputManager.addMapping("physics-forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("physics-backward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("physics-left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("physics-right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("physics-rise", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("physics-fall", new KeyTrigger(KeyInput.KEY_LSHIFT));

        inputManager.addListener(actionListener,
                "physics-fly", "physics-forward", "physics-backward",
                "physics-left", "physics-right", "physics-rise", "physics-fall");

        updatePhysicsForce();
    }

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            switch (name) {
                case "physics-fly" -> {
                    if (isPressed) {
                        flyMode = !flyMode;
                        if (!flyMode) {
                            rise = false;
                            fall = false;
                        }
                    }
                }
                case "physics-forward" -> forward = isPressed;
                case "physics-backward" -> backward = isPressed;
                case "physics-left" -> left = isPressed;
                case "physics-right" -> right = isPressed;
                case "physics-rise" -> rise = isPressed;
                case "physics-fall" -> fall = isPressed;
                default -> {
                    return;
                }
            }

            updatePhysicsForce();
        }
    };

    private void updatePhysicsForce() {
        Vector3f force = new Vector3f();

        Vector3f camDir = cam.getDirection().normalize();
        Vector3f camLeft = cam.getLeft().normalize();

        if (forward) {
            force.addLocal(camDir.mult(moveSpeed));
        }
        if (backward) {
            force.subtractLocal(camDir.mult(moveSpeed));
        }
        if (left) {
            force.subtractLocal(camLeft.mult(moveSpeed));
        }
        if (right) {
            force.addLocal(camLeft.mult(moveSpeed));
        }
        if (flyMode && rise) {
            force.addLocal(0, riseSpeed, 0);
        }
        if (flyMode && fall) {
            force.addLocal(0, -riseSpeed, 0);
        }

        physicsControl.forceMap.put("flyCamForce", force);
    }

    @Override
    public void update(float tpf) {
        // PhysicsControl owns position. FlyByCamera's mouse input still owns
        // rotation because its movement mappings were removed above.
    }
}
