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
import java.io.IOException;
import com.mtsharpgrain.gui.GameState;
import com.mtsharpgrain.jvs.ScriptRunner;
import com.mtsharpgrain.node.Check;
import com.mtsharpgrain.node.OnPrintScript;
import com.mtsharpgrain.node.CommandListener;

public class Main extends SimpleApplication {

    public static String version = "v0.1.0-beta";
    public static int VIEW_DISTANCE = 1;
    private com.mtsharpgrain.RenderManager renderManagermg;
    private BlockSelector blockSelector;
    private WorldAccess worldAccess;

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
        ColorRGBA darkBlue = new ColorRGBA(0.02f, 0.05f, 0.12f, 1f);
        viewPort.setBackgroundColor(darkBlue);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        FogFilter fog = new FogFilter();
        fog.setFogColor(darkBlue);
        fog.setFogDistance(VIEW_DISTANCE * 16 * 0.70f);
        fog.setFogDensity(1.5f);
        fpp.addFilter(fog);
        viewPort.addProcessor(fpp);
        // ─────────────────────────────────────────────────────────────────────

        blockSelector = new BlockSelector(cam, rootNode);
        worldAccess = new WorldAccess("worlds/my_world");
        var player = new Player();
        player.setWorldPosition(new Vector3f(1, 1, 1));
        this.renderManagermg = new com.mtsharpgrain.RenderManager(worldAccess, rootNode, assetManager, player, this);

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
        ScriptRunner.loadAndExecuteVisualScript();
    }

    boolean mouseHover = false;

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
        super.destroy();
    }
}
