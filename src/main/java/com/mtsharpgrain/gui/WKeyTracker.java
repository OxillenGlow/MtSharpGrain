package com.mtsharpgrain.gui;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;

/**
 * Simple parallel listener for the W key so other systems (e.g. speed FOV)
 * can query whether forward is currently held without touching FlyByCamera.
 */
public class WKeyTracker implements ActionListener {

    public static final String W_KEY = "WKeyTracker_W";

    private boolean pressed;

    public WKeyTracker(InputManager inputManager) {
        // Same physical key FlyByCamera already uses for forward.
        // A second mapping is fine; jME delivers the event to every listener.
        inputManager.addMapping(W_KEY, new KeyTrigger(KeyInput.KEY_W));
        inputManager.addListener(this, W_KEY);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (W_KEY.equals(name)) {
            pressed = isPressed;
        }
    }

    /** True while the W key is held down. */
    public boolean isPressed() {
        return pressed;
    }
}
