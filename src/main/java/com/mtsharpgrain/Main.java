package com.mtsharpgrain;


import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.input.RawInputListener;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jvisualscripting.Engine;
import com.jvisualscripting.EventGraph;
import com.jvisualscripting.event.StartEventNode;
import com.jvisualscripting.function.EndNode;
import com.jvisualscripting.function.Print;
import java.io.File;
import java.io.IOException;

public class Main extends SimpleApplication {
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private MouseListener mouseListener;
    private Player player;

    public static void main(String[] args) throws IOException {
        //Main app = new Main();
        //app.start();
        EventGraph graph = new EventGraph();
        graph.save(new File("new.jvsz"));
        String[] argy = new String[1];
        argy[0] = "new.jvsz";
        Engine.main(argy);
    }
    @Override
    public void simpleInitApp() {
        
        // We pass 'this' to allow access to settings, inputManager, guiNode, etc.
        TestInit.setup(this, player, worldAccess, mouseListener, blockSelector);
    
        // 3. Initialize RenderManager (needs the fully prepped worldAccess and player)
        this.renderManagermg = new com.mtsharpgrain.RenderManager(worldAccess, rootNode, assetManager, player, this);

        // Initial Chunk setup
        try {
            ChunkPos firstChunk = new ChunkPos(0, 0, 3);
            worldAccess.createChunkAt(firstChunk, 1);
            renderManagermg.markDirty(firstChunk);
        } catch (Exception e) {
            System.out.println("Error creating chunk: " + e.getMessage());
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
