package com.mtsharpgrain;


import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import java.io.IOException;
import com.jvisualscripting.editor.VisualScriptingEditor;

public class Main extends SimpleApplication {
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private MouseListener mouseListener;

    public static void main(String[] args) throws IOException {
        Main app = new Main();
        app.start();
        
    }

    @Override
    public void simpleInitApp() {
        // Maintain default 45-degree FOV, calculate current window aspect ratio, 
        // and extend the view distance range from 0.1 out to 5000 world units.
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(55.0f, aspectRatio, 0.5f, 5000.0f);
        
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
        this.renderManagermg = new com.mtsharpgrain.RenderManager(worldAccess, rootNode, assetManager, player, this);

        // Chunk setup (optional, for testing)
        try {
            ChunkPos firstChunk = new ChunkPos(0, 0, 3);
            worldAccess.createChunkAt(firstChunk, 1);
            renderManagermg.markDirty(firstChunk);
        } catch (Exception e) {
            System.out.println("Error creating chunk");
        }
        
        // Load the default font
        BitmapFont guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        BitmapText ch = new BitmapText(guiFont, false);
    
        // Set properties
        ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        ch.setText("+"); // The crosshair shape
    
        // Center it on screen
        float x = settings.getWidth() / 2 - ch.getLineWidth() / 2;
        float y = settings.getHeight() / 2 + ch.getLineHeight() / 2;
        ch.setLocalTranslation(x, y, 0);
    
        // Attach to the GUI node (2D layer)
        guiNode.attachChild(ch);
    }

    @Override
    public void simpleUpdate(float tpf) {
        // Update the RenderManager
        renderManagermg.tick(
            cam.getLocation().x, 
            cam.getLocation().y, 
            cam.getLocation().z
        );

        // Check for block selection from BlockSelector
        BlockSelection selection = blockSelector.getSelection();
        if (selection != null) {
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
