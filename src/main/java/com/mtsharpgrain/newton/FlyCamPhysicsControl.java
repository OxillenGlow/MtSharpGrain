package com.mtsharpgrain.newton;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.input.FlyByCamera;

public class FlyCamPhysicsControl extends FlyByCamera {
    private boolean flyMode = false;
    private Vector3f moveForce = new Vector3f();
    private float moveSpeed = 10f;
    private float riseSpeed = 5f;
    private PhysicsControl physicsControl;

    public FlyCamPhysicsControl(Camera cam, PhysicsControl physicsControl) {
        super(cam);
        this.physicsControl = physicsControl;
    }

    @Override
    public void registerWithInput(InputManager inputManager) {
        super.registerWithInput(inputManager);

        // Toggle fly mode
        inputManager.addMapping("fly", new KeyTrigger(KeyInput.KEY_F));
        inputManager.addListener(actionListener, "fly");

        // Movement forces
        inputManager.addMapping("forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("backward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("rise", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("fall", new KeyTrigger(KeyInput.KEY_LSHIFT));

        inputManager.addListener(analogListener, "forward", "backward", "left", "right", "rise", "fall");
    }

    private AnalogListener analogListener = new AnalogListener() {
        @Override
        public void onAnalog(String name, float value, float tpf) {
            if (!enabled) return;

            moveForce.set(0, 0, 0);
            Vector3f camDir = cam.getDirection().normalizeLocal();
            Vector3f camLeft = cam.getLeft().normalizeLocal();

            if (name.equals("forward")) moveForce.addLocal(camDir.multLocal(value * moveSpeed));
            else if (name.equals("backward")) moveForce.addLocal(camDir.multLocal(-value * moveSpeed));
            else if (name.equals("left")) moveForce.addLocal(camLeft.multLocal(-value * moveSpeed));
            else if (name.equals("right")) moveForce.addLocal(camLeft.multLocal(value * moveSpeed));
            else if (name.equals("rise") && flyMode) moveForce.addLocal(0, value * riseSpeed, 0);
            else if (name.equals("fall") && flyMode) moveForce.addLocal(0, -value * riseSpeed, 0);

            physicsControl.forceMap.put("flyCamForce", moveForce);
        }
    };

    private ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            if (name.equals("fly") && isPressed) flyMode = !flyMode;
        }
    };

    @Override
    public void update(float tpf) {
        if (!enabled) return;
        super.update(tpf);
    }
}
