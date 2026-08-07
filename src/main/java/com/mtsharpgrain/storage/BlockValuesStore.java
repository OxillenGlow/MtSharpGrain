package com.mtsharpgrain.storage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chunk key/value store for block instances.
 * Files: worlds/<world>/chunk-values/chunk-CX-CZ.xml
 *
 * Lazy-loads per-chunk into memory and saves atomically on mutation.
 * Values are not automatically deleted on block destruction.
 */
public final class BlockValuesStore {

    private static volatile BlockValuesStore INSTANCE;

    public static void init(Path worldFolder) {
        if (INSTANCE == null) {
            synchronized (BlockValuesStore.class) {
                if (INSTANCE == null) INSTANCE = new BlockValuesStore(worldFolder);
            }
        }
    }

    public static BlockValuesStore getInstance() {
        return INSTANCE;
    }

    private final Path folder;
    // chunkKey -> (posKey -> (k->v))
    private final Map<String, Map<String, Map<String, String>>> cache = new ConcurrentHashMap<>();

    private BlockValuesStore(Path worldFolder) {
        this.folder = worldFolder.resolve("chunk-values");
    }

    private static String chunkFileName(int cx, int cz) {
        return String.format("chunk-%d-%d.xml", cx, cz);
    }

    private static String posKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private Map<String, Map<String, String>> loadChunk(int cx, int cz) throws IOException {
        String chunkKey = cx + ":" + cz;
        return cache.computeIfAbsent(chunkKey, k -> {
            Path file = folder.resolve(chunkFileName(cx, cz));
            Map<String, Map<String, String>> result = new HashMap<>();
            if (!Files.exists(file)) return result;
            try (var is = Files.newInputStream(file)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(is);
                var nodes = doc.getDocumentElement().getElementsByTagName("BlockInstance");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    int x = Integer.parseInt(el.getAttribute("x"));
                    int y = Integer.parseInt(el.getAttribute("y"));
                    int z = Integer.parseInt(el.getAttribute("z"));
                    String pKey = posKey(x, y, z);
                    Map<String, String> kv = new HashMap<>();
                    var kvNodes = el.getElementsByTagName("KV");
                    for (int j = 0; j < kvNodes.getLength(); j++) {
                        Element kvEl = (Element) kvNodes.item(j);
                        String key = kvEl.getAttribute("k");
                        String value = kvEl.getTextContent();
                        kv.put(key, value);
                    }
                    result.put(pKey, kv);
                }
            } catch (Exception e) {
                System.err.println("[BlockValuesStore] failed to load chunk values: " + e.getMessage());
            }
            return result;
        });
    }

    public void setValue(int x, int y, int z, String key, String value) throws IOException {
        int cx = floorDiv(x, 16), cz = floorDiv(z, 16);
        var chunk = loadChunk(cx, cz);
        String pKey = posKey(x, y, z);
        Map<String, String> kv = chunk.computeIfAbsent(pKey, k -> new HashMap<>());
        kv.put(key, value);
        saveChunk(cx, cz, chunk);
    }

    public Optional<String> getValue(int x, int y, int z, String key) {
        int cx = floorDiv(x, 16), cz = floorDiv(z, 16);
        try {
            var chunk = loadChunk(cx, cz);
            var kv = chunk.get(posKey(x, y, z));
            if (kv == null) return Optional.empty();
            return Optional.ofNullable(kv.get(key));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void saveChunk(int cx, int cz, Map<String, Map<String, String>> chunk) throws IOException {
        try {
            if (Files.notExists(folder)) Files.createDirectories(folder);
            Path file = folder.resolve(chunkFileName(cx, cz));
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element root = doc.createElement("ChunkBlockValues");
            root.setAttribute("cx", Integer.toString(cx));
            root.setAttribute("cz", Integer.toString(cz));
            doc.appendChild(root);
            for (var entry : chunk.entrySet()) {
                String[] p = entry.getKey().split(",");
                int x = Integer.parseInt(p[0]);
                int y = Integer.parseInt(p[1]);
                int z = Integer.parseInt(p[2]);
                Element bi = doc.createElement("BlockInstance");
                bi.setAttribute("x", Integer.toString(x));
                bi.setAttribute("y", Integer.toString(y));
                bi.setAttribute("z", Integer.toString(z));
                for (var kv : entry.getValue().entrySet()) {
                    Element kvEl = doc.createElement("KV");
                    kvEl.setAttribute("k", kv.getKey());
                    kvEl.setTextContent(kv.getValue());
                    bi.appendChild(kvEl);
                }
                root.appendChild(bi);
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var os = Files.newOutputStream(temp)) {
                t.transform(new DOMSource(doc), new StreamResult(os));
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw new IOException("Failed to save chunk values", e);
        }
    }

    private static int floorDiv(int x, int size) {
        int r = x / size;
        if ((x ^ size) < 0 && (x % size) != 0) r--;
        return r;
    }
}
