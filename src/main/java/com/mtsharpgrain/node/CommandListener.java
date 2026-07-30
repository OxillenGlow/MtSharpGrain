package com.mtsharpgrain.node;

import com.mtsharpgrain.RenderManager;
import com.mtsharpgrain.WorldAccess;
import com.mtsharpgrain.gui.GameState;

/**
 * Handles console block-edit commands. Edits are queued into WorldAccess;
 * Main processes validation and marks the render chunk dirty only after a
 * successful commit.
 */
public class CommandListener implements OnPrintScript.OnPrintListener {

    private final WorldAccess worldAccess;

    public CommandListener(WorldAccess worldAccess, RenderManager ignoredRenderManager) {
        this.worldAccess = worldAccess;
    }

    @Override
    public void onInput(String output) {
        if (output.startsWith("!destroy ")) {
            handleDestroyCommand(output);
        } else if (output.startsWith("!place ")) {
            handlePlaceCommand(output);
        }
    }

    private boolean isAuthorizedForCommand() {
        String playerState = GameState.getPlayerState();
        return playerState.equals("editor")
                || playerState.equals("manager")
                || playerState.equals("admin");
    }

    private void handleDestroyCommand(String command) {
        if (!isAuthorizedForCommand()) {
            System.out.println("Permission denied: You do not have authorization to use !destroy command. Current state: "
                    + GameState.getPlayerState());
            return;
        }
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
            System.out.println("Block destruction queued at (" + x + ", " + y + ", " + z + ")");
        } catch (NumberFormatException e) {
            System.out.println("Error: coordinates must be integers");
        } catch (Exception e) {
            System.out.println("Error destroying block: " + e.getMessage());
        }
    }

    private void handlePlaceCommand(String command) {
        if (!isAuthorizedForCommand()) {
            System.out.println("Permission denied: You do not have authorization to use !place command. Current state: "
                    + GameState.getPlayerState());
            return;
        }
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
            System.out.println("Block placement queued at (" + x + ", " + y + ", " + z + ") with type " + type);
        } catch (NumberFormatException e) {
            System.out.println("Error: x, y, z, and type must be integers");
        } catch (Exception e) {
            System.out.println("Error placing block: " + e.getMessage());
        }
    }
}