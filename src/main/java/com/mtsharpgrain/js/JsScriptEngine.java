package com.mtsharpgrain.js;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;

/**
 * Shared GraalVM JS context. Exposes a global `Block` object that any
 * script run through this engine can call:
 *   Block.place(x, y, z, blockId)
 *   Block.destroy(x, y, z)
 *   Block.get(x, y, z)
 */
public class JsScriptEngine implements AutoCloseable {

    private final Context context;

    public JsScriptEngine(BlockApi blockApi) {
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.EXPLICIT) // only @HostAccess.Export methods are callable from JS
                .allowHostClassLookup(className -> false) // scripts can't reach into arbitrary Java classes
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        context.getBindings("js").putMember("__BlockApi", blockApi);

        context.eval("js",
            "globalThis.Block = {\n" +
            "  place: function(x, y, z, blockId) { __BlockApi.placeBlock(x, y, z, blockId); },\n" +
            "  destroy: function(x, y, z) { __BlockApi.destroyBlock(x, y, z); },\n" +
            "  get: function(x, y, z) { return __BlockApi.getBlock(x, y, z); }\n" +
            "};\n"
        );
    }

    public Value run(String code, String name) {
        return context.eval(Source.newBuilder("js", code, name).buildLiteral());
    }

    public Value runFile(File file) throws IOException {
        return context.eval(Source.newBuilder("js", file).build());
    }

    /** Exposed so other engine classes (e.g. JsChunkGenerator) can share this same context/globals. */
    public Context raw() {
        return context;
    }

    @Override
    public void close() {
        context.close();
    }
}
