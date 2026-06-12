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

        inputManager.addMapping(TOGGLE_FLYCAM, new KeyTrigger(KeyInput.KEY_F));
        inputManager.addListener(this, TOGGLE_FLYCAM);

        System.out.println("FlyCamToggle initialized");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (TOGGLE_FLYCAM.equals(name) && !isPressed) {
            boolean newState = !flyCam.isEnabled();
            flyCam.setEnabled(newState);
            GameState.setokPlace(newState);
            // Fix: grab mouse when FlyCam is ON, release when OFF
            inputManager.setCursorVisible(!newState);

            System.out.println("FlyCam is now " + (newState ? "ON" : "OFF"));
        }
    }
}