package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.mtsharpgrain.gui.Inventory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import com.mtsharpgrain.js.BlockApi;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import java.nio.file.Path;

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
 * Construction, script evaluation, and {@link #tick(float)} happen on the
 * owning mod virtual thread. Engine-facing API calls marshal separately
 * through EngineAccess because the Context and jME are both thread-affine.
 */
public class JsApiBootstrap {

    private final NodeRegistry nodeRegistry = new NodeRegistry();
    private final TickRegistry tickRegistry = new TickRegistry();
    private final GuiApi guiApi;
    private final DataApi dataApi;
    private final MessagingApi messagingApi;
    private final BlockChangeRegistry blockChangeRegistry = new BlockChangeRegistry();
    private final SpatialClickRegistry spatialClickRegistry = new SpatialClickRegistry();
    private final SceneApi sceneDelegate;
    private final EngineSceneApi sceneApi;
    private final Context context;
    
    public JsApiBootstrap(AssetManager assetManager, WorldAccessor worldAccessor, BlockApi blockApi,
                          Camera cam, Node rootNode, Path packDir, String packName,
                          ModPackManager modPackManager, Inventory inventory, EngineAccess engine) {
        // Init all APIs
        this.sceneDelegate = new SceneApi(nodeRegistry, assetManager, worldAccessor, rootNode);
        this.sceneApi = new EngineSceneApi(sceneDelegate, engine);
        this.dataApi = new DataApi(packDir);
        this.messagingApi = new MessagingApi(packName, modPackManager);
        this.guiApi = new GuiApi();

        
        HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
                .allowArrayAccess(true)
                .targetTypeMapping(
                        Double.class,
                        Float.class,
                        null,
                        Double::floatValue
                )
                .build();
        this.context = Context.newBuilder("js")
                        .allowHostAccess(access)
                        .allowHostClassLookup(name -> false)
                        .build();
  
        context.getBindings("js").putMember("Scene", sceneApi);
        context.getBindings("js").putMember("__InventoryApi", new InventoryApi(inventory, engine));
        context.getBindings("js").putMember("__TickRegistry", tickRegistry);
        context.getBindings("js").putMember("Gui", new EngineGuiApi(guiApi, engine));
        context.getBindings("js").putMember("Data", dataApi);
        context.getBindings("js").putMember("Mod", messagingApi);
        context.getBindings("js").putMember("__BlockApi", blockApi);
        context.getBindings("js").putMember("__BlockChangeRegistry", blockChangeRegistry);
        context.getBindings("js").putMember("__SpatialClickRegistry", spatialClickRegistry);
        context.getBindings("js").putMember("Player", new PlayerApi(cam, rootNode, engine));

        context.eval("js",
            "globalThis.Block = {\n" +
            "  place: function(x, y, z, blockId) { __BlockApi.placeBlock(x, y, z, blockId); },\n" +
            "  destroy: function(x, y, z) { __BlockApi.destroyBlock(x, y, z); },\n" +
            "  forceSet: function(x, y, z, blockId) { __BlockApi.forceSetBlock(x, y, z, blockId); },\n" +
            "  get: function(x, y, z) { return __BlockApi.getBlock(x, y, z); }\n" +
            "};\n" +
            "globalThis.Inventory = {\n" +
            "  add: function(blockId, amount) { return __InventoryApi.addItem(blockId, amount); },\n" +
            "  remove: function(blockId, amount) { return __InventoryApi.removeItem(blockId, amount); },\n" +
            "  get: function(blockId) { return __InventoryApi.getAmount(blockId); }\n" +
            "};\n" +
            "globalThis.Engine = {\n" +
            "  onTick: function(fn, tag) { __TickRegistry.onTick(fn, tag === undefined ? \"\" : tag); },\n" +
            "  onBlockChange: function(fn) { __BlockChangeRegistry.onBlockChange(fn); },\n" +
            "  onSpatialLeftClick:  function(fn) { __SpatialClickRegistry.onLeftClick(fn); },\n" +
            "  onSpatialRightClick: function(fn) { __SpatialClickRegistry.onRightClick(fn); }\n" +
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

    // Getta the current mini you gui manager.
    public GuiApi getGuiApi() {
        return guiApi;
    }
    
    public BlockChangeRegistry getBlockChangeRegistry() {
        return blockChangeRegistry;
    }

    public SpatialClickRegistry getSpatialClickRegistry() {
        return spatialClickRegistry;
    }

    public DataApi getDataApi() {
        return dataApi;
    }

    public MessagingApi getMessagingApi() {
        return messagingApi;
    }
}
