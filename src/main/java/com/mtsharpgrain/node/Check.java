package com.mtsharpgrain.node;

import com.jme3.collision.CollisionResult;
import com.jme3.input.controls.ActionListener;
import com.mtsharpgrain.BlockSelection;
import com.mtsharpgrain.BlockSelector;
import com.mtsharpgrain.WorldAccess;
import com.mtsharpgrain.js.mainthread.ModPackManager;
import static com.mtsharpgrain.gui.Master.blockType;

public class Check implements ActionListener {

    public static final String MOUSE_LEFT  = "MouseLeft";
    public static final String MOUSE_RIGHT = "MouseRight";

    private final WorldAccess worldAccess;
    private final BlockSelector blockSelector;

    /**
     * Injected after construction (mods load after Check is registered with
     * the input manager). Null-safe: spatial-click events are simply dropped
     * until this is set.
     */
    private ModPackManager modPackManager;

    public Check(WorldAccess worldAccess, BlockSelector blockSelector) {
        this.worldAccess   = worldAccess;
        this.blockSelector = blockSelector;
    }

    /** Call this after mod packs have been loaded to enable spatial-click events. */
    public void setModPackManager(ModPackManager modPackManager) {
        this.modPackManager = modPackManager;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed) return;
        if (!com.mtsharpgrain.gui.GameState.isOkPlace()) return;

        boolean leftPressed = MOUSE_LEFT.equals(name);

        CollisionResult hit = blockSelector.raycast();
        if (hit == null) return;

        if (BlockSelector.isChunkHit(hit)) {
            // ── Hit a voxel chunk — place or remove a block as before ────────
            BlockSelection selection = blockSelector.selectionFrom(hit, leftPressed);
            if (leftPressed) {
                worldAccess.setBlockAt(selection.x, selection.y, selection.z, blockType);
                System.out.println("Placing block at " + selection + " of type:" + blockType);
            } else {
                worldAccess.removeBlockAt(selection.x, selection.y, selection.z);
                System.out.println("Removing block at " + selection);
            }
        } else {
            // ── Hit a non-chunk spatial — fire spatial-click events to mods ──
            String hitName = BlockSelector.resolveHitName(hit);
            if (hitName != null && modPackManager != null) {
                if (leftPressed) {
                    modPackManager.notifySpatialLeftClick(hitName);
                } else {
                    modPackManager.notifySpatialRightClick(hitName);
                }
            }
        }
    }
}
