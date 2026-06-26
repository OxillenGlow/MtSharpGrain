package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.mtsharpgrain.WorldAccess;
import org.graalvm.polyglot.Context;

/**
 * Main entry point for the JS modding bridge. Owns the {@link JsApiBootstrap}
 * (Scene/Engine/Gui bindings + Context) and exposes a simple init/tick
 * lifecycle for your SimpleApplication to drive.
 *
 * Usage:
 * <pre>{@code
 * JSModifier modifier = new JSModifier();
 * modifier.init(assetManager, rootNode, worldAccess);
 * modifier.runScript(someScriptSource);
 *
 * // in SimpleApplication.simpleUpdate(tpf):
 * modifier.tick(tpf, currentGuiTag); // runs only callbacks tagged with currentGuiTag
 * }</pre>
 *
 * init() and tick(...) must both be called from the render/main thread - the
 * underlying Graal Context is not safe to use across multiple threads.
 */
public class JSModifier {

    private JsApiBootstrap bootstrap;

    /**
     * Wires up NodeRegistry/SceneApi/TickRegistry/GuiApi, builds the Graal
     * Context, and registers the given root node at the fixed handle {@code 0}
     * so scripts have something to attach to immediately.
     *
     * @param assetManager used for creating geometry/materials (e.g. Scene.createCube)
     * @param rootNode     the jME node scripts will attach created nodes under, registered at handle 0
     * @param worldAccess  your real world/chunk storage - wired to Scene.getBlockId via RealWorldAccessor
     */
    public void init(AssetManager assetManager, Node rootNode, WorldAccess worldAccess) {
        WorldAccessor worldAccessor = new RealWorldAccessor(worldAccess);
        this.bootstrap = new JsApiBootstrap(assetManager, worldAccessor);
        this.bootstrap.getNodeRegistry().registerFixed(0L, rootNode);
    }

    /** Evaluates a JS script source against the bridge's Context. Call after init(). */
    public void runScript(String jsSource) {
        requireInitialized();
        bootstrap.getContext().eval("js", jsSource);
    }

    /**
     * Runs only the tick callbacks registered under {@code guiTag} - lets you
     * drive a single gui-tagged script on demand (e.g. "the user is interacting
     * with this widget, run its logic now") instead of ticking every callback
     * every frame.
     *
     * @param tpf    time per frame, same as SimpleApplication.simpleUpdate
     * @param guiTag the tag identifying which gui-bound script(s) to run
     */
    public void tick(float tpf, String guiTag) {
        requireInitialized();
        bootstrap.getTickRegistry().tickTag(tpf, guiTag);
    }

    /** Runs every registered tick callback, regardless of tag. */
    public void tickAll(float tpf) {
        requireInitialized();
        bootstrap.getTickRegistry().tick(tpf);
    }

    public JsApiBootstrap getBootstrap() {
        requireInitialized();
        return bootstrap;
    }

    public Context getContext() {
        requireInitialized();
        return bootstrap.getContext();
    }

    private void requireInitialized() {
        if (bootstrap == null) {
            throw new IllegalStateException("JSModifier.init(...) must be called before use");
        }
    }
}
