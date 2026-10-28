package com.mtsharpgrain;

import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme.igui.IGui;
import com.jme3.asset.plugins.FileLocator;
import com.jme.igui.IGuiAppState;
import com.jme.igui.IGuiComponent;
import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;
import com.tools.AssetConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import com.mtsharpgrain.gui.GameState;
import com.mtsharpgrain.gui.WKeyTracker;
import com.mtsharpgrain.js.JsChunkGenerator;
import com.mtsharpgrain.js.mainthread.ModPackManager;
import com.mtsharpgrain.js.mainthread.EngineAccess;
import com.mtsharpgrain.node.Check;
import com.mtsharpgrain.node.OnPrintScript;
import com.mtsharpgrain.node.CommandListener;
import com.mtsharpgrain.storage.WorldData;
import com.mtsharpgrain.gui.Inventory;
import com.mtsharpgrain.newton.*;
import com.mtsharpgrain.storage.WorldGit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends SimpleApplication {

    public static String version = "v0.1.1(beta)";
    public static int VIEW_DISTANCE = 6;
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;
    private com.mtsharpgrain.node.Check check;
    public static final float ZONE_SIZE = 100f;
    private Sun sunObject;
    private Inventory inventory;
    private EngineAccess engineAccess;
    public static String worldname;
    /** Absolute world folder: ~/.mtsharpgrain/worlds/<worldname> */
    public static Path worldDir;
    private Thread vThread;

    private JsChunkGenerator chunkGen;

    private static final long WORLD_SEED = 1234L;

    private static final float BASE_FOV = 85f;
    private static final float SPRINT_FOV = 97f;
    private static final float FOV_LERP_SPEED = 4f;
    private float currentFov = BASE_FOV;
    private WKeyTracker wKeyTracker;
    public static Main INSTANCE;
    private boolean loaded;
    private String pendingPath;private IGui gui;
    private ModPackManager modPackManager;
    
    
    public static void main(String[] args) throws IOException {
        AppSettings settings = new AppSettings(true);
        settings.setFullscreen(false);
        settings.setResolution(1280, 720);
        settings.setTitle("MtSharpGrain-" + version);
        settings.setResizable(true);
        settings.setSamples(4);
        var app = new Main();
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        INSTANCE = this;
        loaded = false;
        GameState.guiState = "launcher";

        gui = IGuiAppState.newRelative(assetManager, stateManager, inputManager, guiNode, cam.getWidth(), cam.getHeight());
        gui.textFont("Interface/Fonts/Console.fnt");
        gui.textFontStyle("bold");
        gui.textSize(0.01f).textColor(ColorRGBA.Blue).textHAlign("right").textVAlign("bottom");
        gui.text("MtSharpGrain " + version, 1f, 0f, true);

        gui.textSize(0.025f).textColor(ColorRGBA.Blue).textHAlign("center").textVAlign("top");
        gui.text("Press [F] to exit full screen [Escape] to close game.", 0.5f, 1f, true);

        gui.imageSize(0.035f, 0.06f).imageAlpha(true).imageColor(ColorRGBA.White)
                .imageHAlign("center").imageVAlign("center");
        gui.image("stuff/pointers.blue_4.png", .5f, .5f, true);

        GameState.setModes(false, false);
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(BASE_FOV, aspectRatio, 0.5f, 180.0f);
        currentFov = BASE_FOV;

        TestInit.init(rootNode, flyCam, assetManager, inputManager);

        flyCam.setEnabled(false);
        flyCam.setRotationSpeed(flyCam.getRotationSpeed() * 1.5f);
        flyCam.setMoveSpeed(flyCam.getMoveSpeed() * 5f);
        inputManager.setCursorVisible(true);

        wKeyTracker = new WKeyTracker(inputManager);

        this.sunObject = new Sun(assetManager, rootNode);
        var dlsr = new com.jme3.shadow.DirectionalLightShadowRenderer(assetManager, 1024 * 4, 1);
        dlsr.setLight(this.sunObject.getLight());
        viewPort.addProcessor(dlsr);
        rootNode.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.CastAndReceive);

        viewPort.setBackgroundColor(new ColorRGBA(247 / 1000f, 45 / 1000f, 0f, 1f));

        try {
            Spatial sky = SkyFactory.createSky(assetManager, "/SkyBox.png", SkyFactory.EnvMapType.EquirectMap);
            sky.setName("sky");
            rootNode.attachChild(sky);
        } catch (Exception e) {
            System.err.println("Failed to load skybox: " + e.getMessage());
            e.printStackTrace();
        }

        com.mtsharpgrain.gui.Master.init(gui, inputManager, stateManager);
    }

    public void enterWorld(String name, String thenPath) {
        if (loaded) leaveWorld();

        worldname = com.mtsharpgrain.storage.WorldGit.sanitize(name);
        worldDir = com.mtsharpgrain.storage.WorldGit.worldDir(worldname);
        System.out.println("World dir: " + worldDir.toAbsolutePath());
        assetManager.registerLocator(worldDir.toString(), FileLocator.class);

        extractFiles(worldDir);
        WorldData.init(worldDir);

        try {
            chunkGen = new JsChunkGenerator(worldDir.resolve("chunkgen.js").toFile(), worldDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load chunkgen.js", e);
        }

        blockSelector = new BlockSelector(cam, rootNode);
        worldAccess = new WorldAccess(worldDir.toString(), chunkGen, WORLD_SEED);
        inventory = new Inventory(worldDir.toString());
        worldAccess.setInventory(inventory);
        var player = new Player();
        this.renderManagermg = new com.mtsharpgrain.RenderManager(
                worldAccess, rootNode, assetManager, player, this, chunkGen, WORLD_SEED);
        worldAccess.setRenderManager(this.renderManagermg);

        var printScript = new OnPrintScript();
        printScript.attach();
        var commandListener = new CommandListener(worldAccess, renderManagermg);
        printScript.addListener(commandListener);

        check = new Check(worldAccess, blockSelector);
        if (!inputManager.hasMapping(Check.MOUSE_LEFT)) {
            inputManager.addMapping(Check.MOUSE_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
            inputManager.addMapping(Check.MOUSE_RIGHT, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
            inputManager.addListener(check, Check.MOUSE_LEFT, Check.MOUSE_RIGHT);
        }

        Vector3f spawn = new Vector3f(10000f, 16f, 0f);
        cam.setLocation(spawn);
        player.setWorldPosition(spawn);

        modPackManager = new com.mtsharpgrain.js.mainthread.ModPackManager();
        engineAccess = new EngineAccess(this);
        modPackManager.setEngineAccess(engineAccess);
        worldAccess.addModifier(modPackManager);
        try {
            modPackManager.loadAll(worldDir.resolve("mod"), assetManager, rootNode,
                    worldAccess, renderManagermg, cam, inventory);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Failed to load mod packs", ex);
        }
        check.setModPackManager(modPackManager);

        this.vThread = Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                worldAccess.processPendingBlockChanges();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        loaded = true;
        var alreadySeen = "true".equals(WorldData.get("startScreened"));
        var hasIntro = Files.exists(worldDir.resolve("start.txt")) || Files.exists(worldDir.resolve("start.png"));
        if (!alreadySeen && hasIntro && "home".equals(thenPath)) {
            GameState.guiState = "startscreen";
        } else {
                GameState.navigateTo(thenPath);
        }
        if (sunObject != null) sunObject.loadTime();
    } 
    
    public boolean isWPressed() {
        return wKeyTracker != null && wKeyTracker.isPressed();
    }

    @Override
    public void simpleUpdate(float tpf) {

        if (gui == null) return;
        if (!loaded) {
            com.mtsharpgrain.gui.Master.tic(gui, null, null);
            if (sunObject != null) sunObject.update(tpf, cam.getLocation());
            return;
        }

        Vector3f camPos = cam.getLocation();
        Vector3f shift = new Vector3f(
            (float) (Math.floor(camPos.x / ZONE_SIZE) * ZONE_SIZE),
            0,
            (float) (Math.floor(camPos.z / ZONE_SIZE) * ZONE_SIZE)
        );

        if (!shift.equals(Vector3f.ZERO)) {
            rootNode.getLocalTranslation().subtractLocal(shift);
            rootNode.setLocalTranslation(rootNode.getLocalTranslation());
            cam.setLocation(camPos.subtract(shift));
        }

        com.mtsharpgrain.gui.Master.tic(gui, modPackManager, inventory);
        for (int[] change : worldAccess.drainCommittedChanges()) {
            renderManagermg.onBlockChanged(change[0], change[1], change[2]);
        }
        Vector3f trueWorldPos = cam.getLocation().subtract(rootNode.getLocalTranslation());
        renderManagermg.tick(trueWorldPos.x, trueWorldPos.y, trueWorldPos.z);
        modPackManager.tick(tpf, "Update");
        modPackManager.draw(gui);
        modPackManager.processGuiClicks(tpf);

        sunObject.update(tpf, trueWorldPos);

        boolean isMovingFast = isWPressed();
        float targetFov = isMovingFast ? SPRINT_FOV : BASE_FOV;
        currentFov += (targetFov - currentFov) * Math.min(1f, tpf * FOV_LERP_SPEED);
        float aspect = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(currentFov, aspect, 0.5f, 5000.0f);
    }

    @Override
    public void simpleRender(RenderManager rm) {}

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        if (cam == null) return;
        float aspectRatio = (float) width / height;
        cam.setFrustumPerspective(BASE_FOV, aspectRatio, 0.5f, 5000.0f);
        currentFov = BASE_FOV;
        if (!(gui == null)) {
            gui.destroy();
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
        IGuiComponent crosshair = gui.image("stuff/pointers.blue_4.png", .5f, .5f, true);

    }

    @Override
    public void destroy() {
        if (worldAccess != null) worldAccess.saveAll();
        if (chunkGen != null) {
            try {
                chunkGen.close();
            } catch (IllegalStateException e) {
                System.err.println("[Main] chunkGen.close() failed to close cleanly: " + e.getMessage());
            }
        }
        this.vThread.interrupt();
        sunObject.saveTime();
        if (modPackManager != null) modPackManager.onClose();
        if (inventory != null) inventory.onClose();

        super.destroy();

    }

    private static Path resolveWorldDir(String name) {
        var dir = Paths.get(System.getProperty("user.home"), ".mtsharpgrain", "worlds", name);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create world dir: " + dir, e);
        }
        return dir;
    }

    private void extractFiles(Path dir) {
        try {
            AssetConverter.extract("/chunkgen.js", dir.resolve("chunkgen.js").toString());
            AssetConverter.extract("/mods/blocktrailmod.js", dir.resolve("mod/BlockTrail/blocktrailmod.js").toString());
            AssetConverter.extract("/mods/426.js", dir.resolve("mod/GeoHasher/426.js").toString());
            AssetConverter.extract("/mods/bridge.js", dir.resolve("mod/InstantBridges/bridge.js").toString());
            AssetConverter.extract("/mods/confetti.js", dir.resolve("mod/ConfettiMAIN/confetti.js").toString());
            AssetConverter.extract("/mods/teleport.js", dir.resolve("mod/TeleportMeMAIN/teleport.js").toString());
            AssetConverter.extract("/mods/SurvivalFramework/03_death.js", dir.resolve("mod/SurvivalFramework/death.js").toString());
            AssetConverter.extract("/mods/SurvivalFramework/05_location.js", dir.resolve("mod/SurvivalFramework/location.js").toString());
            AssetConverter.extract("/mods/simpleNPC.js", dir.resolve("mod/SimpleNPC/simpleNPC.js").toString());
            AssetConverter.extract("/mods/TimerDemo.js", dir.resolve("mod/Timer/TimerDemo.js").toString());
            AssetConverter.extract("/mods/Delayed.js", dir.resolve("mod/TimerTest2/Delay.js").toString());
            AssetConverter.extract("/mods/orbs.js", dir.resolve("mod/Lights/orbs.js").toString());
            AssetConverter.extract("/mods/platform.js", dir.resolve("mod/Platforms/platform.js").toString());
            AssetConverter.extract("/mods/status.js", dir.resolve("mod/LFT-Coordinates/status.js").toString());
            AssetConverter.extract("/stuff/start.txt", dir.resolve("start.txt").toString());
            AssetConverter.extract("/stuff/start.png", dir.resolve("start.png").toString());
            AssetConverter.extract("/mods/nostrFinder.js", dir.resolve("mod/Mtsharpgrain-ChatroomMAIN/nostr.js").toString());
            
            System.out.println("Extracted files → " + dir.toAbsolutePath());
        } catch (IOException ex) {
            throw new RuntimeException("Failed extracting world files to " + dir, ex);
        }
    }

    public void leaveWorld() {
        if (!loaded) return;
        if (worldAccess != null) worldAccess.saveAll();
        if (chunkGen != null) {
            try { chunkGen.close(); } catch (Exception ignored) {}
        }
        if (vThread != null) vThread.interrupt();
        if (sunObject != null) sunObject.saveTime();
        if (modPackManager != null) modPackManager.onClose();
        if (inventory != null) inventory.onClose();
        rootNode.detachAllChildren();
        worldAccess = null;
        chunkGen = null;
        modPackManager = null;
        inventory = null;
        renderManagermg = null;
        loaded = false;
        worldDir = null;
        GameState.navigateTo("launcher");
    }
}
