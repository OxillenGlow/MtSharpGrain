package com.mtsharpgrain;

import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseButtonTrigger;
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
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.FogFilter;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;
import com.tools.AssetConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import com.mtsharpgrain.gui.GameState;
import com.mtsharpgrain.js.JsChunkGenerator;
import com.mtsharpgrain.js.mainthread.JSModifier;
import com.mtsharpgrain.js.mainthread.ModPackManager;
import com.mtsharpgrain.jvs.ScriptRunner;
import com.mtsharpgrain.node.Check;
import com.mtsharpgrain.node.OnPrintScript;
import com.mtsharpgrain.node.CommandListener;
import com.mtsharpgrain.gui.Inventory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends SimpleApplication {

    public static String version = "v0.1.1(beta)";
    public static int VIEW_DISTANCE = 1;
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private com.mtsharpgrain.node.Check check;
    public static final float ZONE_SIZE = 100f;
    private Sun sunObject;
    private Inventory inventory;

    // Single JsChunkGenerator instance for the whole app. It owns one GraalVM
    // Context + one dedicated "js-chunk-gen" thread, and is shared by both
    // WorldAccess (synchronous on-demand generation for block edits) and
    // RenderManager (asynchronous streaming generation as the player moves).
    private JsChunkGenerator chunkGen;

    // Fixed for now — wire this up to a real save/load value later if worlds
    // need to be regenerable/reproducible across sessions.
    private static final long WORLD_SEED = 1234L;

    public static void main(String[] args) throws IOException {
        System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        AppSettings settings = new AppSettings(true);
        settings.setFullscreen(false);
        settings.setResolution(1280, 720);
        settings.setTitle("MtSharpGrain-" + version + " .jvs enabled");
        settings.setResizable(true);
        System.out.println("new Main");
        
        var app = new Main();
        System.out.println("set settings settings");
        app.setSettings(settings);
        System.out.println("show settings false");
        
        app.setShowSettings(false);// for other people compiling a jmonkeyengine game for mac, remember to set this orelse its a bunch of buggs later.
        System.out.println("Calling start()...");
        
        app.start();
    }

    private IGui gui;
    private ModPackManager modPackManager;
    
    @Override
    public void simpleInitApp() {

        // This takes some files out of resources and extracts them to world folder.
        extractFiles("my_world");
        
        gui = IGuiAppState.newRelative(assetManager, stateManager, inputManager, guiNode, cam.getWidth(), cam.getHeight());
        gui.textFont("Interface/Fonts/Console.fnt");
        gui.textFontStyle("bold");
        gui.textSize(0.01f).textColor(ColorRGBA.Blue).textHAlign("right").textVAlign("bottom");
        IGuiComponent text = gui.text("MtSharpGrain " + version, 1f, 0f, true);
        
        gui.textSize(0.025f).textColor(ColorRGBA.Blue).textHAlign("center").textVAlign("top");
        IGuiComponent text2 = gui.text("Press [F] to exit/enter full screen [Escape] to close.", 0.5f, 1f, true);

        gui.imageSize(0.035f, 0.06f).imageAlpha(true).imageColor(ColorRGBA.White)
           .imageHAlign("center").imageVAlign("center");
        IGuiComponent crosshair = gui.image("img/pointers.blue_4.png", .5f, .5f, true);

        GameState.setModes(false, false);
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(70f, aspectRatio, 0.5f, 5000.0f);
        cam.setFrustumFar(180f);

        
        TestInit.init(rootNode, flyCam, assetManager, inputManager);
        
        flyCam.setEnabled(false);
        com.jme3.light.DirectionalLight sun = null;
        for (com.jme3.light.Light l : rootNode.getLocalLightList()) {
            if (l instanceof com.jme3.light.DirectionalLight) {
                sun = (com.jme3.light.DirectionalLight) l;
                break;
            }
        }

        this.sunObject = new Sun(assetManager, rootNode);
        
        
        // Testing shadows
        com.jme3.shadow.DirectionalLightShadowRenderer dlsr =
            new com.jme3.shadow.DirectionalLightShadowRenderer(assetManager, 1024, 1);
        dlsr.setLight(sun);
        
        viewPort.addProcessor(dlsr);
        rootNode.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive);

        // ── Background & distance fog ─────────────────────────────────────────
        ColorRGBA darkBlue = new ColorRGBA(247/1000f , 45/1000f , 0f , 1f );//rgba(247, 51, 10, 0.8)
        viewPort.setBackgroundColor(darkBlue);

        //var fpp = new FilterPostProcessor(assetManager);
        //FogFilter fog = new FogFilter();
        //fog.setFogColor(darkBlue);
        //fog.setFogDistance(VIEW_DISTANCE * 16 * 0.90f);
        //fog.setFogDensity(0.8f);
        //fpp.addFilter(fog);
        //viewPort.addProcessor(fpp);
        // ───────────────────────────────────────────────────────────────[...]

        // ── Skybox setup ─────────────────────────────────────────────────────
        try {
            // Load the skybox as an equirectangular map from the single SkyBox.png image
            Spatial sky = SkyFactory.createSky(assetManager, "/SkyBox.png", SkyFactory.EnvMapType.EquirectMap);
            rootNode.attachChild(sky);
        } catch (Exception e) {
            System.err.println("Failed to load skybox: " + e.getMessage());
            e.printStackTrace();
        }
        // ───────────────────────────────────────────────────────────────[...]

        // ── Chunk generator: loads chunkgen.js once and binds the Chunk.* API.
        // templatesRoot must be the directory CONTAINING storageAir/ and
        // storageGround/, since chunkgen.js's Chunk.pickFile("storageAir", ...)
        // resolves relative to it. Adjust this path if templates live elsewhere.
        try {
            chunkGen = new JsChunkGenerator(
                new File("worlds/my_world/chunkgen.js"),
                Paths.get("worlds/my_world")
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load chunkgen.js", e);
        }

        blockSelector = new BlockSelector(cam, rootNode);
        // WorldAccess now needs the generator + seed so ensureChunk() can run
        // chunkBuild() instead of falling back to the flat-fill BufferedChunk(pos) constructor.
        worldAccess = new WorldAccess("worlds/my_world", chunkGen, WORLD_SEED);
        inventory = new Inventory("worlds/my_world");
        worldAccess.setInventory(inventory);
        var player = new Player();
        player.setWorldPosition(new Vector3f(1, 1, 1));
        // Same chunkGen + seed handed to RenderManager so streamed chunks use
        // the identical generation pipeline as on-demand block-edit chunks.
        this.renderManagermg = new com.mtsharpgrain.RenderManager(
            worldAccess, rootNode, assetManager, player, this, chunkGen, WORLD_SEED
        );

        OnPrintScript printScript = new OnPrintScript();
        printScript.attach();
        CommandListener commandListener = new CommandListener(worldAccess, renderManagermg);
        printScript.addListener(commandListener);

        check = new Check(worldAccess, renderManagermg, blockSelector);
        inputManager.addMapping(Check.MOUSE_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(Check.MOUSE_RIGHT, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addListener(check, Check.MOUSE_LEFT, Check.MOUSE_RIGHT);
        
        //CompletableFuture.runAsync(() -> {
        //    ScriptRunner.loadAndExecuteVisualScript();
        //});
        
        Vector3f spawn = new Vector3f(10000f, 16f, 0f);
        cam.setLocation(spawn);
        player.setWorldPosition(spawn);

        // Mods are loaded with new modPackManager that gives mods different contexts
        modPackManager = new com.mtsharpgrain.js.mainthread.ModPackManager();
        try {
            modPackManager.loadAll(Paths.get("worlds/my_world/mod"), assetManager, rootNode,
                    worldAccess, renderManagermg, cam, inventory);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Failed to load mod packs", ex);
        }

        worldAccess.addModifier(modPackManager);
        check.setModPackManager(modPackManager); // enable spatial-click events now that mods are loaded
        
        flyCam.setMoveSpeed(flyCam.getMoveSpeed() * 3f);// fly cam is too slow
    }

    @Override
    public void simpleUpdate(float tpf) {

        // Floating origin: keep camera's render-space position small.
        Vector3f camPos = cam.getLocation();
        Vector3f shift = new Vector3f(
            (float) (Math.floor(camPos.x / ZONE_SIZE) * ZONE_SIZE),
            0, // usually don't shift vertical, unless huge Y ranges in the future
            (float) (Math.floor(camPos.z / ZONE_SIZE) * ZONE_SIZE)
        );

        if (!shift.equals(Vector3f.ZERO)) {
            rootNode.getLocalTranslation().subtractLocal(shift);
            rootNode.setLocalTranslation(rootNode.getLocalTranslation()); // trigger transform refresh
            cam.setLocation(camPos.subtract(shift));
        }
        
        com.mtsharpgrain.gui.Master.tic(gui, modPackManager, inventory);// just noticed tic is misspelled! wont fix
        Vector3f trueWorldPos = cam.getLocation().subtract(rootNode.getLocalTranslation());
        renderManagermg.tick(trueWorldPos.x, trueWorldPos.y, trueWorldPos.z);
        modPackManager.tick(tpf, "Update");
        modPackManager.draw(gui);
        modPackManager.processGuiClicks(tpf);

        sunObject.update(tpf, trueWorldPos);
    }

    @Override
    public void simpleRender(RenderManager rm) {}

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        if (cam == null) return;
        float aspectRatio = (float) width / height;
        cam.setFrustumPerspective(55.0f, aspectRatio, 0.5f, 5000.0f);
        if (!(gui == null)) {
            gui.destroy();  // Properly detach and clean up the old GUI
        }
        gui = IGuiAppState.newRelative(assetManager, stateManager, inputManager, guiNode, cam.getWidth(), cam.getHeight());
        gui.textFont("Interface/Fonts/Console.fnt");
        gui.textFontStyle("bold");
        gui.textSize(0.01f).textColor(ColorRGBA.Blue).textHAlign("right").textVAlign("bottom");
        IGuiComponent text = gui.text("MtSharpGrain " + version, 1f, 0f, true);
        
        gui.textSize(0.025f).textColor(ColorRGBA.Blue).textHAlign("center").textVAlign("top");
        IGuiComponent text2 = gui.text("Press [F] to exit/enter full screen [Escape] to close.", 0.5f, 1f, true);

        gui.imageSize(0.035f, 0.06f).imageAlpha(true).imageColor(ColorRGBA.White)
           .imageHAlign("center").imageVAlign("center");
        IGuiComponent crosshair = gui.image("img/pointers.blue_4.png", .5f, .5f, true);
        
    }

    @Override
    public void destroy() {
        if (worldAccess != null) worldAccess.saveAll();
        // Shuts down the js-chunk-gen thread and closes the GraalVM Context.
        // Must happen after saveAll() in case anything triggers a last-second
        // generation (it won't currently, but keeps shutdown order sane).
        if (chunkGen != null) {
            try {
                chunkGen.close();
            } catch (IllegalStateException e) {
                System.err.println("[Main] chunkGen.close() failed to close cleanly: " + e.getMessage());
            }
        }
        if (modPackManager != null) modPackManager.onClose();
        if (inventory != null) inventory.onClose();
        
        super.destroy();
        
    }

    private void extractFiles(String world) {
      
        try {
            AssetConverter.extract("/chunkgen.js", "worlds/"+world+"/chunkgen.js");
            AssetConverter.extract("/mods/blocktrailmod.js", "worlds/"+world+"/mod/BlockTrail/blocktrailmod.js");
            AssetConverter.extract("/mods/426.js", "worlds/"+world+"/mod/GeoHasher/426.js");
            AssetConverter.extract("/mods/bridge.js", "worlds/"+world+"/mod/InstantBridges/bridge.js");
            AssetConverter.extract("/mods/confetti.js", "worlds/"+world+"/mod/Confetti/confetti.js");
            AssetConverter.extract("/mods/teleport.js", "worlds/"+world+"/mod/TeleportMe/teleport.js");
            AssetConverter.extract("/mods/SurvivalFramework/03_death.js", "worlds/"+world+"/mod/SurvivalFramework/death.js");
            AssetConverter.extract("/mods/SurvivalFramework/05_location.js", "worlds/"+world+"/mod/SurvivalFramework/location.js");
           
            System.out.println("Extracted default mod files");
        } catch (IOException ex) {
            System.out.println("failed"+ex);   
        }
        
    }
}
