package com.mtsharpgrain.node;

import com.jme3.input.controls.ActionListener;
import com.mtsharpgrain.BlockSelection;
import com.mtsharpgrain.BlockSelector;
import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;

public class Check implements ActionListener {

    public static final String MOUSE_LEFT = "MouseLeft";
    public static final String MOUSE_RIGHT = "MouseRight";

    private final WorldAccess worldAccess;
    private final RenderManager renderManager;
    private final BlockSelector blockSelector;

    public Check(WorldAccess worldAccess, RenderManager renderManager, BlockSelector blockSelector) {
        this.worldAccess = worldAccess;
        this.renderManager = renderManager;
        this.blockSelector = blockSelector;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed) return;
        if (!com.mtsharpgrain.gui.GameState.isOkPlace()) return;

        boolean leftPressed = MOUSE_LEFT.equals(name);
        BlockSelection selection = blockSelector.getSelection(leftPressed);
        if (selection == null) return;

        if (leftPressed) {
            worldAccess.setBlockAt(selection.x, selection.y, selection.z, 2);
            System.out.println("Placed block at " + selection);
        } else {
            worldAccess.removeBlockAt(selection.x, selection.y, selection.z);
            System.out.println("Removed block at " + selection);
        }
        renderManager.onBlockChanged(selection.x, selection.y, selection.z);
    }
}
