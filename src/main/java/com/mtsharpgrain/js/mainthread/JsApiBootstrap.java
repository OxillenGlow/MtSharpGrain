package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import com.mtsharpgrain.js.BlockApi;

/**
 * Wires together the registries/APIs and builds the Graal Context.
 *
 * Usage:
 * <pre>{@code
 * JsApiBootstrap bootstrap = new JsApiBootstrap(assetManager, myWorldAccessor);
 * bootstrap.getNodeRegistry().registerFixed(0L, rootNode); // expose world root at a fixed handle
 * bootstrap.getContext().eval("js", someScriptSource);
 *
 * // in SimpleApplication.simpleUpdate(tpf):
 * bootstrap.tick(tpf);
 * }</pre>
 *
 * Construction and {@link #tick(float)} must both happen on the render/main
 * thread - the Context is not thread-safe to use otherwise.
 */
public class JsApiBootstrap {

    private final NodeRegistry nodeRegistry = new NodeRegistry();
    private final TickRegistry tickRegistry = new TickRegistry();
    private final GuiApi guiApi;
    private final SceneApi sceneApi;
    private final Context context;
    
    public JsApiBootstrap(AssetManager assetManager, WorldAccessor worldAccessor, BlockApi blockApi) {
        this.sceneApi = new SceneApi(nodeRegistry, assetManager, worldAccessor);
        this.guiApi = new GuiApi(nodeRegistry);

        HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT).build();
        this.context = Context.newBuilder("js")
                .allowHostAccess(access)
                .allowHostClassLookup(name -> false)
                .build();

        context.getBindings("js").putMember("Scene", sceneApi);
        context.getBindings("js").putMember("Engine", tickRegistry);
        context.getBindings("js").putMember("Gui", guiApi);

        context.getBindings("js").putMember("__BlockApi", blockApi);
        context.eval("js",
            "globalThis.Block = {\n" +
            "  place: function(x, y, z, blockId) { __BlockApi.placeBlock(x, y, z, blockId); },\n" +
            "  destroy: function(x, y, z) { __BlockApi.destroyBlock(x, y, z); },\n" +
            "  get: function(x, y, z) { return __BlockApi.getBlock(x, y, z); }\n" +
            "};\n"
        );
    }

    public Context getContext() {
        return context;
    }

    public NodeRegistry getNodeRegistry() {
        return nodeRegistry;
    }

    public TickRegistry getTickRegistry() {
        return tickRegistry;
    }

    /** Call from SimpleApplication.simpleUpdate(tpf), on the render thread only. */
    public void tick(float tpf) {
        tickRegistry.tick(tpf);
    }
}
