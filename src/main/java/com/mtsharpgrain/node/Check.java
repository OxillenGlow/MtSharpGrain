package com.mtsharpgrain.node;

import com.jme3.input.controls.ActionListener;
import com.mtsharpgrain.BlockSelection;
import com.mtsharpgrain.BlockSelector;
import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;

public class Check implements ActionListener {

    public static final String ACTION_PLACE = "PlaceBlock";
    public static final String ACTION_BREAK = "BreakBlock";

    private final WorldAccess worldAccess;
    private final RenderManager renderManager;
    public Check(WorldAccess worldAccess, RenderManager renderManager) {
        this.worldAccess = worldAccess;
        this.renderManager = renderManager;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed) return; // fire only on press, not release
        if (!com.mtsharpgrain.gui.GameState.isOkPlace()) return;

        BlockSelection selection = blockSelector.getSelection();
        if (selection == null) return;

        if (ACTION_PLACE.equals(name)) {
            worldAccess.setBlockAt(selection.x, selection.y, selection.z, 2);
            System.out.println("Placed block at " + selection);
            renderManager.onBlockChanged(selection.x, selection.y, selection.z);
        } else if (ACTION_BREAK.equals(name)) {
            worldAccess.removeBlockAt(selection.x, selection.y, selection.z);
            System.out.println("Removed block at " + selection);
            renderManager.onBlockChanged(selection.x, selection.y, selection.z);
        }
    }
}
