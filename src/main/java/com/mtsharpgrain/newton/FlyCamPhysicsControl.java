package com.mtsharpgrain.newton;

import com.jme3.input.CameraInput;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.AnalogListener;
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
    private final Vector3f moveForce = new Vector3f();
    private float moveSpeed = 10f;
    private float riseSpeed = 5f;
    private final PhysicsControl physicsControl;

    public FlyCamPhysicsControl(Camera cam, PhysicsControl physicsControl) {
        super(cam);
        this.physicsControl = physicsControl;
    }

    @Override
    public void registerWithInput(InputManager inputManager) {
        // Register mouse-look/zoom from FlyByCamera, but remove its direct
        // movement mappings. Otherwise FlyByCamera would move cam directly
        // while PhysicsControl also moves the CameraNode.
        super.registerWithInput(inputManager);

        inputManager.deleteMapping(CameraInput.FLYCAM_FORWARD);
        inputManager.deleteMapping(CameraInput.FLYCAM_BACKWARD);
        inputManager.deleteMapping(CameraInput.FLYCAM_STRAFELEFT);
        inputManager.deleteMapping(CameraInput.FLYCAM_STRAFERIGHT);
        inputManager.deleteMapping(CameraInput.FLYCAM_RISE);
        inputManager.deleteMapping(CameraInput.FLYCAM_LOWER);

        inputManager.addMapping("physics-fly", new KeyTrigger(KeyInput.KEY_F));
        inputManager.addListener(actionListener, "physics-fly");

        inputManager.addMapping("physics-forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("physics-backward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("physics-left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("physics-right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("physics-rise", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("physics-fall", new KeyTrigger(KeyInput.KEY_LSHIFT));

        inputManager.addListener(analogListener,
                "physics-forward", "physics-backward",
                "physics-left", "physics-right",
                "physics-rise", "physics-fall");
    }

    private final AnalogListener analogListener = new AnalogListener() {
        @Override
        public void onAnalog(String name, float value, float tpf) {
            if (!enabled) {
                return;
            }

            moveForce.set(0, 0, 0);

            Vector3f camDir = cam.getDirection().normalizeLocal();
            Vector3f camLeft = cam.getLeft().normalizeLocal();

            if (name.equals("physics-forward")) {
                moveForce.addLocal(camDir.mult(value * moveSpeed));
            } else if (name.equals("physics-backward")) {
                moveForce.addLocal(camDir.mult(-value * moveSpeed));
            } else if (name.equals("physics-left")) {
                moveForce.addLocal(camLeft.mult(-value * moveSpeed));
            } else if (name.equals("physics-right")) {
                moveForce.addLocal(camLeft.mult(value * moveSpeed));
            } else if (name.equals("physics-rise") && flyMode) {
                moveForce.addLocal(0, value * riseSpeed, 0);
            } else if (name.equals("physics-fall") && flyMode) {
                moveForce.addLocal(0, -value * riseSpeed, 0);
            }

            physicsControl.forceMap.put("flyCamForce", moveForce.clone());
        }
    };

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            if (name.equals("physics-fly") && isPressed) {
                flyMode = !flyMode;
            }
        }
    };

    @Override
    public void update(float tpf) {
        // PhysicsControl owns camera position. FlyByCamera's input callbacks
        // still handle mouse rotation/zoom, but its position callbacks were
        // removed from the input mappings above.
    }
}
