package com.mtsharpgrain;

import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.ViewPort;
import com.jme3.math.Vector3f;

public class ChunkUnloadControl extends AbstractControl {

    private final RenderManager renderManager;
    private final ChunkPos chunkPos;
    private final Player player;

    /**
     *
     * @param renderManager
     * @param chunkPos
     * @param player
     */
    public ChunkUnloadControl(RenderManager renderManager, ChunkPos chunkPos, Player player) {
        this.renderManager = renderManager;
        this.chunkPos = chunkPos;
        this.player = player;
    }

    @Override
    protected void controlUpdate(float tpf) {
        Vector3f playerPos = player.getWorldPosition();  // Get player's world position
        Vector3f chunkPosWorld = spatial.getWorldTranslation();  // Get chunk's world position

        // Check if the chunk is too far from the player
        if (playerPos.distanceSquared(chunkPosWorld) > Main.VIEW_DISTANCE* Main.VIEW_DISTANCE) {
            spatial.removeFromParent();  // Remove the chunk from the scene graph
            renderManager.unloadChunk(chunkPos);  // Ensure this method exists and handles chunk unloading
            setEnabled(false);  // Disable this control to avoid further updates
        }
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, ViewPort vp) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}