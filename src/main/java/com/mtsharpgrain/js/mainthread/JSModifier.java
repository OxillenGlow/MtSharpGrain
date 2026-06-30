package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.mtsharpgrain.WorldAccess;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

import java.io.File;
import java.io.IOException;

/**
 * Main entry point for the JS modding bridge. Owns the {@link JsApiBootstrap}
 * (Scene/Engine/Gui bindings + Context) and exposes a simple init/tick
 * lifecycle for your SimpleApplication to drive.
 *
 * Usage:
 * <pre>{@code
 * JSModifier modifier = new JSModifier();
 * modifier.init(assetManager, rootNode, worldAccess);
 * modifier.runJs(new File("worlds/my_world/mod/test.js"));
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
    
    public void init(AssetManager assetManager, Node rootNode, WorldAccess worldAccess, RenderManager renderManager) {
        WorldAccessor worldAccessor = new RealWorldAccessor(worldAccess);
        BlockApi blockApi = new BlockApi(worldAccess, renderManager);
        this.bootstrap = new JsApiBootstrap(assetManager, worldAccessor, blockApi);
        this.bootstrap.getNodeRegistry().registerFixed(0L, rootNode);
    }

    /**
     * Reads and evaluates a JS file against the bridge's Context. Call after init().
     *
     * Builds a Graal {@link Source} from the file (rather than reading it into a
     * String yourself and eval-ing that) so stack traces and error messages
     * reference the real file name/path - much easier to debug than an
     * anonymous "unnamed" source.
     *
     * @param scriptFile e.g. new File("worlds/my_world/mod/test.js")
     * @throws IOException if the file can't be read
     */
    public void runJs(File scriptFile) throws IOException {
        requireInitialized();
        if (!scriptFile.isFile()) {
            throw new IOException("Script file not found: " + scriptFile.getAbsolutePath());
        }
        Source source = Source.newBuilder("js", scriptFile).build();
        try {
            bootstrap.getContext().eval(source);
        } catch (PolyglotException e) {
            // TODO: route to your real logger instead of stderr
            System.err.println("[JSModifier] error running script '" + scriptFile.getName() + "': " + e.getMessage());
            throw e;
        }
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
