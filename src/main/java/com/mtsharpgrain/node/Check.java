package com.mtsharpgrain.node;

import com.jme3.collision.CollisionResult;
import com.jme3.input.controls.ActionListener;
import com.mtsharpgrain.BlockSelection;
import com.mtsharpgrain.BlockSelector;
import com.mtsharpgrain.WorldAccess;
import com.mtsharpgrain.js.mainthread.ModPackManager;
import static com.mtsharpgrain.gui.Master.blockType;
import static java.lang.System.out;

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
        out.println(System.currentTimeMillis() + "------> Click detected" + name + isPressed + tpf);
        if (!isPressed) return;
        if (!com.mtsharpgrain.gui.GameState.isOkPlace()){
            out.println(System.currentTimeMillis() +"ignored b/c not in \"play\" state");
            return;
        }

        boolean leftPressed = MOUSE_LEFT.equals(name);
        CollisionResult hit = blockSelector.raycast();
        out.println(System.currentTimeMillis() + "Collision detection finished");
        
        if (hit == null) return;

        if (BlockSelector.isChunkHit(hit)) {
            System.out.println(System.currentTimeMillis()+ "block interaction");
            
            // ── Hit a voxel chunk — place or remove a block as before ────────
            BlockSelection selection = blockSelector.selectionFrom(hit, leftPressed);
            if (leftPressed) {
                System.out.println(System.currentTimeMillis()+"Placing block at " + selection + " of type:" + blockType);
                worldAccess.setBlockAt(selection.x, selection.y, selection.z, blockType);
                System.out.println(System.currentTimeMillis()+"Finished: " + selection + " of type:" + blockType);
            } else {
                System.out.println(System.currentTimeMillis()+"Removing block at " + selection);
                worldAccess.removeBlockAt(selection.x, selection.y, selection.z);
                System.out.println(System.currentTimeMillis()+"Finished: " + selection + " of type:" + blockType);
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
