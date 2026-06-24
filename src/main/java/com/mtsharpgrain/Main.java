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
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import com.mtsharpgrain.gui.GameState;
import com.mtsharpgrain.js.JsChunkGenerator;
import com.mtsharpgrain.jvs.ScriptRunner;
import com.mtsharpgrain.node.Check;
import com.mtsharpgrain.node.OnPrintScript;
import com.mtsharpgrain.node.CommandListener;
import java.util.concurrent.CompletableFuture;

public class Main extends SimpleApplication {

    public static String version = "v0.1.0-beta";
    public static int VIEW_DISTANCE = 1;
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;

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
        Main app = new Main();
        app.setSettings(settings);
        app.start();
    }

    private IGui gui;

    @Override
    public void simpleInitApp() {
        gui = IGuiAppState.newRelative(assetManager, stateManager, inputManager, guiNode, cam.getWidth(), cam.getHeight());
        gui.textFont("Interface/Fonts/Default.fnt");
        gui.textFontStyle("bold");
        gui.textSize(0.02f).textColor(ColorRGBA.Blue).textHAlign("right").textVAlign("bottom");
        IGuiComponent text = gui.text("MtSharpGrain " + version, 1f, 0f, true);

        GameState.setModes(true, true);
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(55.0f, aspectRatio, 0.5f, 5000.0f);
        TestInit.init(rootNode, flyCam, assetManager, inputManager);

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

        // ── Background & distance fog ─────────────────────────────────────────
        ColorRGBA darkBlue = new ColorRGBA(247/1000f , 45/1000f , 0f , 1f );//rgba(247, 51, 10, 0.8)
        viewPort.setBackgroundColor(darkBlue);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        FogFilter fog = new FogFilter();
        fog.setFogColor(darkBlue);
        fog.setFogDistance(VIEW_DISTANCE * 16 * 0.90f);
        fog.setFogDensity(1.5f);
        fpp.addFilter(fog);
        viewPort.addProcessor(fpp);
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

        BitmapFont guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        BitmapText ch = new BitmapText(guiFont, false);
        ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        ch.setText("+");
        float x = cam.getWidth() / 2f - ch.getLineWidth() / 2f;
        float y = cam.getHeight() / 2f + ch.getLineHeight() / 2f;
        ch.setLocalTranslation(x, y, 0);

        Check check = new Check(worldAccess, renderManagermg, blockSelector);
        inputManager.addMapping(Check.MOUSE_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(Check.MOUSE_RIGHT, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addListener(check, Check.MOUSE_LEFT, Check.MOUSE_RIGHT);

        guiNode.attachChild(ch);
        CompletableFuture.runAsync(() -> {
            ScriptRunner.loadAndExecuteVisualScript();
        });
        
        Vector3f spawn = new Vector3f(10000f, 10f, 0f);
        cam.setLocation(spawn);
        player.setWorldPosition(spawn);
    }

    @Override
    public void simpleUpdate(float tpf) {
        com.mtsharpgrain.gui.Master.tic(gui);
        renderManagermg.tick(cam.getLocation().x, cam.getLocation().y, cam.getLocation().z);
    }

    @Override
    public void simpleRender(RenderManager rm) {}

    @Override
    public void destroy() {
        if (worldAccess != null) worldAccess.saveAll();
        // Shuts down the js-chunk-gen thread and closes the GraalVM Context.
        // Must happen after saveAll() in case anything triggers a last-second
        // generation (it won't currently, but keeps shutdown order sane).
        if (chunkGen != null) chunkGen.close();
        super.destroy();
    }
}
