package com.mygame;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.input.RawInputListener;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;


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
        // Maintain default 45-degree FOV, calculate current window aspect ratio, 
        // and extend the view distance range from 0.1 out to 5000 world units.
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(45.0f, aspectRatio, 0.5f, 5000.0f);
        
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
        
        // 1. Set the solid background canvas color (e.g., Sky Blue)
        viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 1.0f, 1.0f));

        // 2. Create the round ball mesh representing the sun
        Sphere sunMesh = new Sphere(32, 32, 5.0f); // 32 radial samples, 32 slices, radius 5
        Geometry sunGeo = new Geometry("SunBall", sunMesh);

        // 3. Make the sun glow using an Unshaded Material definition
        Material sunMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        sunMat.setColor("Color", ColorRGBA.Yellow); // The basic color of the sun
    
        // Optional: Enable a neon glow if you have post-processing filters active
        sunMat.setColor("GlowColor", ColorRGBA.White); 
        sunGeo.setMaterial(sunMat);
        // This forces the object to render behind everything else and bypass standard distance clipping
        sunGeo.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Sky);
        sunGeo.setCullHint(Spatial.CullHint.Never); // Prevents JME from culling it out
        
        // 4. Move the sun ball up into the sky and attach it
        sunGeo.setLocalTranslation(0, 1000, -2000); 
        rootNode.attachChild(sunGeo);
        
        
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
