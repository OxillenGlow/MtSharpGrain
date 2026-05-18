package com.mtsharpgrain;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.input.MouseInput;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;

public class MouseListener implements RawInputListener {
    public boolean leftPressed = false;
    public boolean rightPressed = false;

    @Override
    public void onKeyEvent(KeyInputEvent evt) {
        // We are not using keyboard events, so leave this empty
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent evt) {
        // We are not handling mouse motion, but the method needs to be implemented
    }

    
    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        if (evt.getButtonIndex() == MouseInput.BUTTON_LEFT) {
            leftPressed = evt.isPressed();
            System.out.println("Left button pressed: " + leftPressed); // Debugging output
        } else if (evt.getButtonIndex() == MouseInput.BUTTON_RIGHT) {
            rightPressed = evt.isPressed();
            System.out.println("Right button pressed: " + rightPressed); // Debugging output
        }
    }

    @Override
    public void onTouchEvent(TouchEvent evt) {
    }

    @Override
    public void beginInput() {
    }

    @Override
    public void endInput() {
    }

    @Override
    public void onJoyAxisEvent(JoyAxisEvent evt) {
        
    }

    @Override
    public void onJoyButtonEvent(JoyButtonEvent evt) {
        
    }

    
}