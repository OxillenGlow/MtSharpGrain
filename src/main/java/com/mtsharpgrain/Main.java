package com.mtsharpgrain;

//needs imports
import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.system.JmeCanvasContext;
import java.awt.Canvas;
import java.io.IOException;
import com.jvisualscripting.editor.VisualScriptingEditor;
import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main extends SimpleApplication {
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private MouseListener mouseListener;

    public static void main(String[] args) throws IOException {
        //I am seeing if embedding in jFrame works
        System.out.println("0");
        //Main app = new Main();
        //app.start();
        // 1. Create the JFrame on the EDT
        
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        System.out.println("0");
        javax.swing.JFrame frame = new javax.swing.JFrame("Test");
        System.out.println("1");
        Main app = new Main();
        System.out.println("2");
        AppSettings settings = new AppSettings(true);
        settings.setWidth(1280);
        settings.setHeight(720);
        System.out.println("3");
        app.setSettings(settings);

        app.createCanvas();
        System.out.println("4");
        JmeCanvasContext ctx =
                (JmeCanvasContext) app.getContext();
        System.out.println("5");
        Canvas canvas = ctx.getCanvas();
        System.out.println("6");
        canvas.setSize(1280, 720);
        System.out.println("7");
        System.out.println("8");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        System.out.println("9");
        frame.add(canvas);
        System.out.println("10");
        frame.pack();
        System.out.println("11");
        frame.setSize(1280, 720);
        System.out.println("12");
        frame.setVisible(true);
        System.out.println("13");
        app.startCanvas();
        System.out.println("14");
    
        
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
