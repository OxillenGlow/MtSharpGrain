package com.mtsharpgrain.js;

import com.mtsharpgrain.BufferedChunk;
import com.mtsharpgrain.ChunkPos;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;

/**
 * Loads a generator script that defines chunkBuild(x, y, z, seed) and
 * calls it once per chunk. x/y/z are CHUNK coordinates — multiply by 16
 * inside the script if you need the world-space corner of the chunk.
 */
public class JsChunkGenerator {

    private final Context context;

    public JsChunkGenerator(JsScriptEngine engine, File generatorScript) throws IOException {
        this.context = engine.raw(); // shares the same context/globals as Block, etc.
        context.eval(Source.newBuilder("js", generatorScript).build());
    }

    public BufferedChunk generate(ChunkPos pos, long seed) {
        BufferedChunk chunk = new BufferedChunk(pos);
        ChunkWriterApi writer = new ChunkWriterApi(chunk);

        context.getBindings("js").putMember("__ChunkWriter", writer);
        context.eval("js",
            "globalThis.Chunk = {\n" +
            "  set: function(x, y, z, id) { __ChunkWriter.set(x, y, z, id); },\n" +
            "  get: function(x, y, z) { return __ChunkWriter.get(x, y, z); }\n" +
            "};\n"
        );

        Value chunkBuildFn = context.getBindings("js").getMember("chunkBuild");
        if (chunkBuildFn == null || !chunkBuildFn.canExecute()) {
            throw new IllegalStateException("Generator script has no chunkBuild(x, y, z, seed) function");
        }
        chunkBuildFn.execute(pos.getX(), pos.getY(), pos.getZ(), seed);

        return chunk;
    }
}
