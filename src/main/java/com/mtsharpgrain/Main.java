package com.mtsharpgrain;

//needs imports
import com.jme.igui.IGui;
import com.jme.igui.IGuiAppState;
import com.jme.igui.IGuiComponent;
import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import java.io.IOException;
import java.io.File;
import com.mtsharpgrain.gui.GameState;
import com.mtsharpgrain.gui.SwingStarter;
import com.mtsharpgrain.jvs.ScriptRunner;
import com.mtsharpgrain.node.Check;
import com.mtsharpgrain.node.OnPrintScript;
import com.mtsharpgrain.node.CommandListener;

public class Main extends SimpleApplication {

    public static String version = "v0.1.0-beta";
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private MouseListener mouseListener;
    
    
    
    public static void main(String[] args) throws IOException {
        
        //Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        AppSettings settings = new AppSettings(true);
        settings.setFullscreen(false);
        settings.setResolution(1280, 720);
        settings.setTitle("MtSharpGrain-"+version+" .jvs enabled");
        //settings.setResolution(360, 250);
        System.out.println("0");
        Main app = new Main();
        app.setSettings(settings);
        //app.setShowSettings(false); 
        app.start();
         
    }
    private IGui gui;
  
    @Override
    public void simpleInitApp() {
        System.out.println("0");
        
        // -------------------------- GUI ------------------------------
        gui=IGuiAppState.newRelative(assetManager,stateManager,inputManager,guiNode, cam.getWidth(),cam.getHeight());
        // gui.destroy();
        gui.textFont("Interface/Fonts/Default.fnt");
        gui.textFontStyle("bold");
        gui.textSize(0.02f).textColor(ColorRGBA.Blue).textHAlign("right").textVAlign("bottom");
        IGuiComponent text = gui.text("MtSharpGrain" + version,0.9f,0f,true); // persistent. stays for ever
        // -------------------------- GUI ------------------------------
        
        GameState.setModes(true, true);
        System.out.println("14");
        // Maintain default 45-degree FOV, calculate current window aspect ratio, 
        // and extend the view distance range from 0.1 out to 5000 world units.
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(55.0f, aspectRatio, 0.5f, 5000.0f);
        // Your existing initialization logic
        TestInit.init(rootNode, flyCam, assetManager, inputManager);
        // Get the sun light from rootNode since TestInit still returns null
        com.jme3.light.DirectionalLight sun = null;
        for (com.jme3.light.Light l : rootNode.getLocalLightList()) {
             if (l instanceof com.jme3.light.DirectionalLight) {
                sun = (com.jme3.light.DirectionalLight) l;
                break;
            }
        }

        com.jme3.shadow.DirectionalLightShadowRenderer dlsr =
    new com.jme3.shadow.DirectionalLightShadowRenderer(assetManager, 512, 1);
        dlsr.setLight(sun);
        viewPort.addProcessor(dlsr);

        rootNode.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive);
        
        // THIS STUFF DOES NOT WORK, EITHER CLAUDE WROTE A TON OF TRASH OR IM DOING SOME IMPORTS WRONG
        // Initialize SkyControl with Mars settings
        //var skyControl = SkyControlInit.initMarsSky(rootNode, cam, assetManager);
        //
        // Initialize day-night cycle: 60 minutes real time = 1 full day cycle
        // 60 minutes = 3600 seconds
        //dayNightCycle = new DayNightCycleManager(skyControl, 3600f);
        
        // Initialize the MouseListener first
        mouseListener = new MouseListener();
        blockSelector = new BlockSelector(cam, rootNode, mouseListener);
        inputManager.addRawInputListener(mouseListener);
        worldAccess = new WorldAccess("worlds/my_world");
        var player = new Player();
        player.setWorldPosition(new Vector3f(1, 1, 1));
        this.renderManagermg = new com.mtsharpgrain.RenderManager(worldAccess, rootNode, assetManager, player, this);
        OnPrintScript printScript = new OnPrintScript();
        printScript.attach();
        CommandListener commandListener = new CommandListener(worldAccess, renderManagermg);
        printScript.addListener(commandListener);

        
        
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
        
        // Load and execute .jvsz visual script file
        ScriptRunner.loadAndExecuteVisualScript();
    }
    boolean mouseHover=false;
   
    @Override
    public void simpleUpdate(float tpf) {
        com.mtsharpgrain.gui.Master.tic(gui);
        
        
        
        // Update the RenderManager
        renderManagermg.tick(
            cam.getLocation().x, 
            cam.getLocation().y, 
            cam.getLocation().z
        );

        // Check for block selection from BlockSelector
        Check.tick(worldAccess, renderManagermg, blockSelector);
        
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
