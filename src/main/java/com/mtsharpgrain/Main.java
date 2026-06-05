package com.mtsharpgrain;

//needs imports
import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme.igui.IGuiAppState;
import com.jme.igui.IGuiComponent;
import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import java.io.IOException;
import com.jvisualscripting.editor.VisualScriptingEditor;
import com.mtsharpgrain.gui.GameState;
import com.mtsharpgrain.gui.SwingStarter;

public class Main extends SimpleApplication {
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private MouseListener mouseListener;
    IGui gui;
    boolean mouseHover=false;
    boolean mousePressedL=false;
    boolean mousePressedR=false;
    
    public static void main(String[] args) throws IOException {
        
        System.out.println("0");
        Main app = new Main();
        app.start();
         
    }
  
    @Override
    public void simpleInitApp() {
        GameState.setModes(true, true);
        System.out.println("14");
        // Maintain default 45-degree FOV, calculate current window aspect ratio, 
        // and extend the view distance range from 0.1 out to 5000 world units.
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(55.0f, aspectRatio, 0.5f, 5000.0f);
        // Your existing initialization logic
        TestInit.init(rootNode, flyCam, assetManager);
        // Get the sun light from rootNode since TestInit still returns null
        com.jme3.light.DirectionalLight sun = null;
        for (com.jme3.light.Light l : rootNode.getLocalLightList()) {
             if (l instanceof com.jme3.light.DirectionalLight) {
                sun = (com.jme3.light.DirectionalLight) l;
                break;
            }
        }

        com.jme3.shadow.DirectionalLightShadowRenderer dlsr =
            new com.jme3.shadow.DirectionalLightShadowRenderer(assetManager, 2048, 3);
        dlsr.setLight(sun);
        viewPort.addProcessor(dlsr);

        rootNode.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive);
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
        flyCam.setEnabled(false);
        gui=IGuiAppState.newRelative(assetManager,stateManager,inputManager,guiNode, cam.getWidth(),cam.getHeight());
        // gui.destroy();
        gui.textFont("igui/vera/VeraBd.ttf");
        gui.textFontStyle("bold");
        gui.textSize(0.05f).textColor(ColorRGBA.Red).textHAlign("center").textVAlign("top");
        IGuiComponent text=gui.text("Test IGui",.5f,1f,true); // persistent. stays for ever
        // text.destroy();   
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
        com.mtsharpgrain.node.Check.tick(worldAccess, renderManagermg, blockSelector);
        gui.push(false);
        gui.textFont("igui/vera/Vera.ttf");

        gui.textSize(0.02f);
        gui.textColor(mouseHover?ColorRGBA.Red:ColorRGBA.White);
        gui.textHAlign("left");
        gui.textVAlign("top");


        float spacing=0.03f;
        float line=1;
        gui.text("Line1",0f,line,(event,arg)->{
            if(event==IGuiMouseEvent.MOUSE_IN){
                mouseHover=true;
            }else if(event==IGuiMouseEvent.MOUSE_OUT){
                mouseHover=false;
            }
            return true;
        });

        gui.textColor(mousePressedL?ColorRGBA.Green:ColorRGBA.White);
        gui.text("Line2",0f,line-=spacing,(event,arg)->{
            if(event==IGuiMouseEvent.MOUSE_PRESSED_LEFT){
                mousePressedL=true;
            }else if(event==IGuiMouseEvent.MOUSE_RELEASED_LEFT){
                mousePressedL=false;
            }
            return true;
        });

        gui.textColor(mousePressedR?ColorRGBA.Green:ColorRGBA.White);
        gui.text("Line3",0f,line-=spacing,(event,arg)->{
            if(event==IGuiMouseEvent.MOUSE_PRESSED_RIGHT){
                mousePressedR=true;
            }else if(event==IGuiMouseEvent.MOUSE_RELEASED_RIGHT){
                mousePressedR=false;
            }
            return true;
        });


        gui.textColor(ColorRGBA.White);
        float fps=timer.getFrameRate();
        gui.textColor(fps>=59?ColorRGBA.Green:ColorRGBA.Red);
        gui.textHAlign("right");
        gui.text("FPS: "+((int)fps),1f,1f);
        gui.imageSize(0.3f,IGui.SIZE_AUTO);
        gui.imageHAlign("center");
        gui.imageVAlign("center");
        gui.imageScale(0.5f);
        gui.image("igui/goldmonkey_head.png", .5f, .5f);
        gui.pop();

        gui.push(false);
        gui.imageSize(IGui.SIZE_AUTO,1f);
        gui.imageHAlign("center");
        gui.imageVAlign("center");
        gui.zIndex(-1);
        gui.image("igui/wall.jpg", .5f, .5f);
        gui.pop();
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
