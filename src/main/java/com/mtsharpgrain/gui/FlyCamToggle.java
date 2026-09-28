package com.mtsharpgrain.gui;

import com.jme3.input.FlyByCamera;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;

public class FlyCamToggle implements ActionListener {

    private static final String TOGGLE_FLYCAM = "ToggleFlyCam";
    private final FlyByCamera flyCam;
    private final InputManager inputManager;

    public FlyCamToggle(InputManager inputManager, FlyByCamera flyCam) {
        this.flyCam = flyCam;
        this.inputManager = inputManager;

        inputManager.addMapping(TOGGLE_FLYCAM, new KeyTrigger(KeyInput.KEY_F), new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addListener(this, TOGGLE_FLYCAM);

        System.out.println("FlyCamToggle initialized");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (TOGGLE_FLYCAM.equals(name) && !isPressed) {
            // Only allow F to *exit* play mode. Entering is done via the Play button.
            if (!flyCam.isEnabled()) {
                return; // already in menu / not in play → ignore F
            }

            // Exit play
            flyCam.setEnabled(false);
            GameState.setokPlace(false);
            GameState.exitPlay();
            inputManager.setCursorVisible(true);

            System.out.println("FlyCam is now OFF (exited play via F)");
        }
    }
}
