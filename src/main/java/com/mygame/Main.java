package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.input.RawInputListener;

public class Main extends SimpleApplication {
    private com.mygame.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private MouseListener mouseListener;

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        // Your existing initialization logic
        TestInit.init(rootNode, flyCam, assetManager);

        // Initialize the MouseListener first
        mouseListener = new MouseListener();

        // Ensure camera and rootNode are available before initializing BlockSelector
        blockSelector = new BlockSelector(cam, rootNode, mouseListener);

        // Register the MouseListener with the input manager
        inputManager.addRawInputListener(mouseListener);

        // Initialize WorldAccess and RenderManager
        worldAccess = new WorldAccess("worlds/my_world");
        var player = new Player();
        player.setWorldPosition(new Vector3f(1, 1, 1));

        // Initialize RenderManager
        this.renderManagermg = new com.mygame.RenderManager(worldAccess, rootNode, assetManager, player, this);

        // Chunk setup (optional, for testing)
        try {
            ChunkPos firstChunk = new ChunkPos(0, 0, 3);
            worldAccess.createChunkAt(firstChunk, 1);
            renderManagermg.markDirty(firstChunk);
        } catch (Exception e) {
            System.out.println("Error creating chunk");
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        // Update the RenderManager
        renderManagermg.tick(
            cam.getLocation().x, 
            cam.getLocation().y, 
            cam.getLocation().z
        );

        // Check for block coordinates from BlockSelector
        Object[] blockCoords = blockSelector.getBlockCoords();
        if (blockCoords != null) {
            // Handle block coordinates here (e.g., block placement or destruction)
            System.out.println(blockCoords[0].toString());
            System.out.println(blockCoords[1].toString());
            System.out.println(blockCoords[2].toString());
            System.out.println("60fps");
        }
        else {System.out.println("Error getting imput");}
    }

    @Override
    public void simpleRender(RenderManager rm) {
        // Optional: Render logic
    }

    @Override
    public void destroy() {
        // Save world if needed
        if (worldAccess != null) {
            worldAccess.saveAll();
        }
        super.destroy(); // Standard shutdown
    }
}