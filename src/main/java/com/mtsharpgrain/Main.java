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
import com.tools.AssetConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import com.mtsharpgrain.gui.GameState;
import com.mtsharpgrain.js.JsChunkGenerator;
import com.mtsharpgrain.js.mainthread.JSModifier;
import com.mtsharpgrain.jvs.ScriptRunner;
import com.mtsharpgrain.node.Check;
import com.mtsharpgrain.node.OnPrintScript;
import com.mtsharpgrain.node.CommandListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends SimpleApplication {

    public static String version = "v0.1.0-beta";
    public static int VIEW_DISTANCE = 2;
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    public static final float ZONE_SIZE = 100f;
    

    // Single JsChunkGenerator instance for the whole app. It owns one GraalVM
    // Context + one dedicated "js-chunk-gen" thread, and is shared by both
    // WorldAccess (synchronous on-demand generation for block edits) and
    // RenderManager (asynchronous streaming generation as the player moves).
    private JsChunkGenerator chunkGen;

    // Fixed for now — wire this up to a real save/load value later if worlds
    // need to be regenerable/reproducible across sessions.
    private static final long WORLD_SEED = 1234L;

    public static void main(String[] args) throws IOException {
        AppSettings settings = new AppSettings(true);
        settings.setFullscreen(false);
        settings.setResolution(1280, 720);
        settings.setTitle("MtSharpGrain-" + version + " .jvs enabled");
        settings.setResizable(true);
        
        var app = new Main();
        app.setSettings(settings);
        app.start();
    }

    private IGui gui;
    private JSModifier modifier;

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

        gui.textSize(0.025f).textColor(ColorRGBA.White).textHAlign("center").textVAlign("center");
        IGuiComponent text3 = gui.text("+", .5f, .5f, true);

        GameState.setModes(false, false);
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(70f, aspectRatio, 0.5f, 5000.0f);
        cam.setFrustumFar((VIEW_DISTANCE * 16f) + 100);

        
        TestInit.init(rootNode, flyCam, assetManager, inputManager);
        flyCam.setEnabled(false);
        
        com.jme3.light.DirectionalLight sun = null;
        for (com.jme3.light.Light l : rootNode.getLocalLightList()) {
            if (l instanceof com.jme3.light.DirectionalLight) {
                sun = (com.jme3.light.DirectionalLight) l;
                break;
            }
        }
        
        
        // Testing shadows
        com.jme3.shadow.DirectionalLightShadowRenderer dlsr =
            new com.jme3.shadow.DirectionalLightShadowRenderer(assetManager, 512, 1);
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
        // ─────────────────────────────────────────────────────────────────────

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

        Check check = new Check(worldAccess, renderManagermg, blockSelector);
        inputManager.addMapping(Check.MOUSE_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(Check.MOUSE_RIGHT, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addListener(check, Check.MOUSE_LEFT, Check.MOUSE_RIGHT);

        guiNode.attachChild(ch);
        //CompletableFuture.runAsync(() -> {
        //    ScriptRunner.loadAndExecuteVisualScript();
        //});
        
        Vector3f spawn = new Vector3f(10000f, 16f, 0f);
        cam.setLocation(spawn);
        player.setWorldPosition(spawn);


        // adding JS mods that run on main thread
        // TODO: Moving to another thread! Very important.
        modifier = new JSModifier();
        modifier.init(assetManager, rootNode, worldAccess, renderManagermg, cam);
        // Main.simpleInitApp — replace the single modifier.runJs(...) block
        try {
            Path modRoot = Paths.get("worlds/my_world/mod");
            if (Files.exists(modRoot)) {
                try (var walk = Files.walk(modRoot)) {
                    walk.filter(p -> p.toString().endsWith(".js"))
                    .sorted() // deterministic load order across platforms
                    .forEach(p -> {
                        try {
                             modifier.runJs(p.toFile());
                        } catch (IOException ex) {
                            Logger.getLogger(Main.class.getName()).log(Level.SEVERE,
                            "Failed to load mod script: " + p, ex);
                        }
                    });
                }
            } else {
                Logger.getLogger(Main.class.getName()).log(Level.WARNING,
                "Mod folder not found, skipping: " + modRoot.toAbsolutePath());
            }
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Failed to walk mod folder", ex);
        }

        worldAccess.addModifier(modifier);
        
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
        
        com.mtsharpgrain.gui.Master.tic(gui);// just noticed tic is misspelled! wont fix
        Vector3f trueWorldPos = cam.getLocation().subtract(rootNode.getLocalTranslation());
        renderManagermg.tick(trueWorldPos.x, trueWorldPos.y, trueWorldPos.z);
        modifier.tick(tpf, "Update");
        com.mtsharpgrain.gui.Master.tic(gui);
        modifier.draw(gui);
        modifier.processGuiClicks(tpf);
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

        gui.textSize(0.025f).textColor(ColorRGBA.White).textHAlign("center").textVAlign("center");
        IGuiComponent text3 = gui.text("+", .5f, .5f, true);
    }

    @Override
    public void destroy() {
        if (worldAccess != null) worldAccess.saveAll();
        // Shuts down the js-chunk-gen thread and closes the GraalVM Context.
        // Must happen after saveAll() in case anything triggers a last-second
        // generation (it won't currently, but keeps shutdown order sane).
        if (chunkGen != null) chunkGen.close();
        super.destroy();
        
    }

    private void extractFiles(String world) {
        try {
            AssetConverter.extract("/chunkgen.js", "worlds/"+world+"/chunkgen.js");
            AssetConverter.extract("/test.js", "worlds/"+world+"/mod/test.js");
            AssetConverter.extract("/blocktrailmod.js", "worlds/"+world+"/mod/blocktrailmod.js");
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract bundled world scripts", e);
        }
    }
}
