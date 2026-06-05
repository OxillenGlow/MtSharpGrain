package com.mtsharpgrain.node;

import com.mtsharpgrain.*;
public class Check {
    public static void tick(com.mtsharpgrain.WorldAccess wa,RenderManager rm) {
        BlockSelection selection = blockSelector.getSelection();
        if (selection != null && com.mtsharpgrain.gui.GameState.isOkPlace()) {
            if (selection.placeAction) {
                // Left click: place block (ID 2)
                worldAccess.setBlockAt(selection.x, selection.y , selection.z, 2);
                System.out.println("Placed block at " + selection);
            } else {
                // Right click: remove block (set to 0)
                worldAccess.removeBlockAt(selection.x , selection.y , selection.z );
                System.out.println("Removed block at " + selection);
            }
            // Notify RenderManager to rebuild affected chunks
            renderManagermg.onBlockChanged(selection.x , selection.y , selection.z );
        }
    }
}
