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
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

public class JsChunkGenerator implements AutoCloseable {

    private final Context context;
    private final ChunkArrayApi arrayApi;
    private final ExecutorService genThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "js-chunk-gen");
        t.setDaemon(true);
        return t;
    });

    public JsChunkGenerator(File generatorScript, Path templatesRoot) throws IOException {
        context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.EXPLICIT)
            .allowHostClassLookup(className -> false)
            .option("engine.WarnInterpreterOnly", "false")
            .build();
        arrayApi = new ChunkArrayApi(templatesRoot);
        context.getBindings("js").putMember("__ChunkArrayApi", arrayApi);
        context.eval(Source.newBuilder("js", generatorScript).build());
    }

    public CompletableFuture<BufferedChunk> generateAsync(ChunkPos pos, long seed) {
        CompletableFuture<BufferedChunk> future = new CompletableFuture<>();
        genThread.submit(() -> {
            try { future.complete(generate(pos, seed)); }
            catch (Throwable t) { future.completeExceptionally(t); }
        });
        return future;
    }

    /** Blocking variant — only for code paths that need a chunk *right now* (e.g. a single block edit). */
    public BufferedChunk generateSync(ChunkPos pos, long seed) {
        try {
            return generateAsync(pos, seed).join();
        } catch (CompletionException e) {
            throw new RuntimeException("JS chunk generation failed for " + pos, e.getCause() != null ? e.getCause() : e);
        }
    }

    @Override
    public void close() {
        genThread.shutdown();
        context.close();
    }

    private BufferedChunk generate(ChunkPos pos, long seed) {
        BufferedChunk chunk = new BufferedChunk(pos);
        ChunkWriterApi writer = new ChunkWriterApi(chunk);
        context.getBindings("js").putMember("__ChunkWriter", writer);
        context.eval("js",
            "globalThis.Chunk = {\n" +
            "  set: function(x, y, z, id) { __ChunkWriter.set(x, y, z, id); },\n" +
            "  get: function(x, y, z) { return __ChunkWriter.get(x, y, z); },\n" +
            "  setArray: function(flat) { __ChunkWriter.setArray(flat); },\n" +
            "  load: function(path) { return __ChunkArrayApi.getChunkAsArray(path); },\n" +
            "  pickFile: function(folder, roll) { return __ChunkArrayApi.pickFile(folder, roll); }\n" +
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
