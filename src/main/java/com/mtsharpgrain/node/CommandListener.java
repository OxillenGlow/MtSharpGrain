package com.mtsharpgrain.node;

import com.mtsharpgrain.WorldAccess;
import com.mtsharpgrain.RenderManager;

/**
 * CommandListener extends OnPrintScript.OnPrintListener to handle console commands
 * for block manipulation in the game world.
 * 
 * Supported commands:
 *   !destroy x y z - Remove block at coordinates
 *   !place x y z type - Place block at coordinates with given type
 */
public class CommandListener implements OnPrintScript.OnPrintListener {
    
    private WorldAccess worldAccess;
    private RenderManager renderManager;
    
    public CommandListener(WorldAccess wa, RenderManager rm) {
        this.worldAccess = wa;
        this.renderManager = rm;
    }
    
    @Override
    public void onInput(String output) {
        if (output.startsWith("!destroy ")) {
            handleDestroyCommand(output);
        } else if (output.startsWith("!place ")) {
            handlePlaceCommand(output);
        }
    }
    
    /**
     * Handle !destroy x y z command
     */
    private void handleDestroyCommand(String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 4) {
                System.out.println("Usage: !destroy x y z");
                return;
            }
            
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            
            worldAccess.removeBlockAt(x, y, z);
            renderManager.onBlockChanged(x, y, z);
            System.out.println("Block destroyed at (" + x + ", " + y + ", " + z + ")");
            
        } catch (NumberFormatException e) {
            System.out.println("Error: coordinates must be integers");
        } catch (Exception e) {
            System.out.println("Error destroying block: " + e.getMessage());
        }
    }
    
    /**
     * Handle !place x y z type command
     */
    private void handlePlaceCommand(String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 5) {
                System.out.println("Usage: !place x y z type");
                return;
            }
            
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            int type = Integer.parseInt(parts[4]);
            
            worldAccess.setBlockAt(x, y, z, type);
            renderManager.onBlockChanged(x, y, z);
            System.out.println("Block placed at (" + x + ", " + y + ", " + z + ") with type " + type);
            
        } catch (NumberFormatException e) {
            System.out.println("Error: x, y, z, and type must be integers");
        } catch (Exception e) {
            System.out.println("Error placing block: " + e.getMessage());
        }
    }
}
