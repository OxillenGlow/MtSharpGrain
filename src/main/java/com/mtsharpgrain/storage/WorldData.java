package com.mtsharpgrain.storage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple world-level string key/value store.
 * File: worlds/&lt;world&gt;/data.xml
 *
 * Usage:
 *   WorldData.init(Paths.get("worlds/" + worldname));
 *   WorldData.set("myTag", "hello");
 *   String v = WorldData.get("myTag");          // null if missing
 *   String v2 = WorldData.get("myTag", "def");
 */
public final class WorldData {

    private static volatile Path file;
    private static final Map<String, String> map = new ConcurrentHashMap<>();

    private WorldData() {}

    /** Call once when a world is loaded. */
    public static void init(Path worldFolder) {
        file = worldFolder.resolve("data.xml");
        map.clear();
        load();
    }

    public static void set(String tag, String value) {
        if (tag == null) return;
        if (value == null) {
            map.remove(tag);
        } else {
            map.put(tag, value);
        }
        save();
    }

    /** @return value or null if the tag is missing */
    public static String get(String tag) {
        return map.get(tag);
    }

    /** @return value or {@code def} if the tag is missing */
    public static String get(String tag, String def) {
        return map.getOrDefault(tag, def);
    }

    public static boolean has(String tag) {
        return map.containsKey(tag);
    }

    public static void remove(String tag) {
        if (map.remove(tag) != null) save();
    }

    // ── persistence ──────────────────────────────────────────────────────

    private static void load() {
        if (file == null || !Files.exists(file)) return;
        try (var is = Files.newInputStream(file)) {
            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(is);
            NodeList nodes = doc.getDocumentElement().getElementsByTagName("Entry");
            for (int i = 0; i < nodes.getLength(); i++) {
                var el = (Element) nodes.item(i);
                var key = el.getAttribute("k");
                var val = el.getTextContent();
                if (key != null && !key.isEmpty()) map.put(key, val != null ? val : "");
            }
        } catch (Exception e) {
            System.err.println("[WorldData] load failed: " + e.getMessage());
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .newDocument();
            var root = doc.createElement("WorldData");
            doc.appendChild(root);

            for (var e : map.entrySet()) {
                var entry = doc.createElement("Entry");
                entry.setAttribute("k", e.getKey());
                entry.setTextContent(e.getValue());
                root.appendChild(entry);
            }

            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            var temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var os = Files.newOutputStream(temp)) {
                t.transform(new DOMSource(doc), new StreamResult(os));
            }
            Files.move(temp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            System.err.println("[WorldData] save failed: " + e.getMessage());
        }
    }
}
