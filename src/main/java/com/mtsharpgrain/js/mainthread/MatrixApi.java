package com.mtsharpgrain.js.mainthread;

import com.mtsharpgrain.node.DynamicBlockRegistry;
import com.mtsharpgrain.storage.BlockValuesStore;
import org.graalvm.polyglot.HostAccess;

import java.util.Optional;

/**
 * Exposed to JS as `Matrix`. Synchronous API (no EngineAccess enqueue).
 *
 * Methods:
 *   addNew(name, builderType, properties) -> int id
 *   setValue(x,y,z,key,value)
 *   getValue(x,y,z,key) -> string|null
 *   getBlockNameMod(id) -> [name, modPack] or null
 *   getProperties(name, modPack)
 *
 * Mod pack name is inferred from the packName provided on construction.
 */
public final class MatrixApi {

    private final String packName;

    public MatrixApi(String packName) {
        this.packName = packName;
    }

    /**
     * New exported overload that accepts diffuse RGBA, specular RGBA and shininess.
     * JS callers can now call Matrix.addNew(name, builderType, propertiesJson,
     *   dr, dg, db, da, sr, sg, sb, sa, shininess)
     */
    @HostAccess.Export
    public int addNew(String name, String builderType, String propertiesJson,
                      double dr, double dg, double db, double da,
                      double sr, double sg, double sb, double sa,
                      double shininess) {
        DynamicBlockRegistry registry = DynamicBlockRegistry.getInstance();
        if (registry == null) throw new IllegalStateException("DynamicBlockRegistry not initialized");
        String b = builderType == null ? "Py" : builderType;
        String props = propertiesJson == null ? "" : propertiesJson;
        // HostAccess maps Double -> Float via JsApiBootstrap HostAccess rules; cast to float for storage.
        return registry.addIfAbsent(name, packName, b, props,
                (float) dr, (float) dg, (float) db, (float) da,
                (float) sr, (float) sg, (float) sb, (float) sa,
                (float) shininess);
    }

    @HostAccess.Export
    public void setValue(int x, int y, int z, String key, String value) {
        BlockValuesStore store = BlockValuesStore.getInstance();
        if (store == null) {
            System.err.println("[MatrixApi] BlockValuesStore not initialized");
            return;
        }
        try {
            store.setValue(x, y, z, key, value == null ? "" : value);
        } catch (Exception e) {
            System.err.println("[MatrixApi] failed to setValue: " + e.getMessage());
        }
    }

    @HostAccess.Export
    public String getValue(int x, int y, int z, String key) {
        BlockValuesStore store = BlockValuesStore.getInstance();
        if (store == null) return null;
        Optional<String> v = store.getValue(x, y, z, key);
        return v.orElse(null);
    }

    @HostAccess.Export
    public String[] getBlockNameMod(int id) {
        DynamicBlockRegistry registry = DynamicBlockRegistry.getInstance();
        if (registry == null) return null;
        var opt = registry.getById(id);
        if (opt.isPresent()) {
            var r = opt.get();
            return new String[]{ r.name(), r.modPack() };
        }
        // Fallback: built-in blocks: return default modpack "DEFAULT" with name from BlockRegistry if available
        try {
            var def = com.mtsharpgrain.node.BlockRegistry.get(id);
            if (def != null) {
                String name = "Block " + id;
                // no built-in "name" mapping available - return generic
                return new String[]{ name, "DEFAULT" };
            }
        } catch (Throwable t) {}
        return null;
    }

    @HostAccess.Export
    public String getProperties(String name, String modPack) {
        DynamicBlockRegistry registry = DynamicBlockRegistry.getInstance();
        if (registry == null) return "";
        String mp = modPack == null || modPack.isEmpty() ? packName : modPack;
        return registry.getProperties(name, mp).orElse("");
    }

    /**
     * Returns the numeric block ID for a dynamically-registered block name in the given mod pack.
     *
     * If {@code modPackName} is null or empty, this Matrix instance's {@code packName} is used.
     * The method returns a boxed {@code Integer} (or {@code null}) so callers can distinguish
     * "not found" / registry-unavailable from a valid numeric ID.
     *
     * Exported to JS as Matrix.getId(name, modPackName).
     *
     * @param name the block name as registered by the mod
     * @param modPackName optional mod pack name; when null/empty defaults to the pack associated with this Matrix
     * @return the block ID (negative dynamic ID) or {@code null} if not found or the dynamic registry isn't initialized
     */
    @HostAccess.Export
    public Integer getId(String name, String modPackName) {
        var registry = DynamicBlockRegistry.getInstance();
        if (registry == null) return null;
        String mp = (modPackName == null || modPackName.isEmpty()) ? packName : modPackName;
        return registry.getId(name, mp).orElse(null);
    }
}
