package com.mtsharpgrain.js;

import com.mtsharpgrain.BufferedChunk;
import com.mtsharpgrain.ChunkPos;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns its own Context, separate from the one BlockApi uses on the main
 * thread for Block.place/Block.destroy. A GraalVM Context can only be
 * entered by one thread at a time, so this gets a dedicated single
 * background thread that is the ONLY thing ever allowed to touch it.
 */
public class JsChunkGenerator implements AutoCloseable {

    private final Context context;
    private final ExecutorService genThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "js-chunk-gen");
        t.setDaemon(true);
        return t;
    });

    public JsChunkGenerator(File generatorScript) throws IOException {
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        context.eval(Source.newBuilder("js", generatorScript).build());
    }

    /** Call from any thread. Generation itself always runs on genThread. */
    public CompletableFuture<BufferedChunk> generateAsync(ChunkPos pos, long seed) {
        CompletableFuture<BufferedChunk> future = new CompletableFuture<>();
        genThread.submit(() -> {
            try {
                future.complete(generate(pos, seed));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    // Only ever invoked on genThread — never call this directly from elsewhere.
    private BufferedChunk generate(ChunkPos pos, long seed) {
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

    @Override
    public void close() {
        genThread.shutdown();
        context.close();
    }
}
