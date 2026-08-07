package com.mtsharpgrain.node;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-scoped dynamic registry for mod-registered blocks.
 *
 * Persisted to worlds/<world>/registered-blocks.xml and buffered in memory.
 * IDs start at BlockRegistry.ID_GLASS + 1 (i.e. first dynamic ID = 11 by default).
 *
 * NOTE: This is a simple implementation tuned to your existing code style.
 */
public final class DynamicBlockRegistry {

    public record Registration(int id, String name, String modPack, String builderType, String propertiesJson) {}

    private static volatile DynamicBlockRegistry INSTANCE;

    private final Path file;
    private final Map<Integer, Registration> byId = new ConcurrentHashMap<>();
    private final Map<String, Integer> index = new ConcurrentHashMap<>();
    private int nextId;

    private static final int BASE_DYNAMIC_ID = BlockRegistry.ID_GLASS + 1; // 11 by default

    private DynamicBlockRegistry(Path worldFolder) {
        this.file = worldFolder.resolve("registered-blocks.xml");
        this.nextId = BASE_DYNAMIC_ID;
        try { load(); } catch (IOException e) {
            System.err.println("[DynamicBlockRegistry] failed to load: " + e.getMessage());
        }
    }

    public static void init(Path worldFolder) {
        if (INSTANCE == null) {
            synchronized (DynamicBlockRegistry.class) {
                if (INSTANCE == null) INSTANCE = new DynamicBlockRegistry(worldFolder);
            }
        }
    }

    public static DynamicBlockRegistry getInstance() {
        return INSTANCE;
    }

    private synchronized void load() throws IOException {
        byId.clear();
        index.clear();
        nextId = BASE_DYNAMIC_ID;
        if (!Files.exists(file)) return;
        try (var is = Files.newInputStream(file)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            var nodes = doc.getDocumentElement().getElementsByTagName("Block");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                int id = Integer.parseInt(el.getAttribute("id"));
                String modPack = el.getAttribute("modPack");
                String name = el.getAttribute("name");
                String builder = el.getAttribute("builder");
                String props = "";
                var propsNodes = el.getElementsByTagName("Properties");
                if (propsNodes.getLength() > 0) props = propsNodes.item(0).getTextContent();
                Registration reg = new Registration(id, name, modPack, builder, props);
                byId.put(id, reg);
                index.put(makeKey(modPack, name), id);
                if (id >= nextId) nextId = id + 1;
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse registered-blocks.xml", e);
        }
    }

    private synchronized void save() throws IOException {
        try {
            if (Files.notExists(file.getParent())) Files.createDirectories(file.getParent());
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element root = doc.createElement("RegisteredBlocks");
            doc.appendChild(root);
            List<Registration> regs = new ArrayList<>(byId.values());
            regs.sort(Comparator.comparingInt(Registration::id));
            for (Registration reg : regs) {
                Element block = doc.createElement("Block");
                block.setAttribute("id", Integer.toString(reg.id()));
                block.setAttribute("modPack", reg.modPack());
                block.setAttribute("name", reg.name());
                block.setAttribute("builder", reg.builderType());
                Element props = doc.createElement("Properties");
                props.setTextContent(reg.propertiesJson() == null ? "" : reg.propertiesJson());
                block.appendChild(props);
                root.appendChild(block);
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var os = Files.newOutputStream(temp)) {
                t.transform(new DOMSource(doc), new StreamResult(os));
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw new IOException("Failed to save registered-blocks.xml", e);
        }
    }

    private static String makeKey(String modPack, String name) {
        return modPack + "|" + name;
    }

    /**
     * Register a new block if absent. Returns existing ID if already registered.
     * Synchronous and persists to disk on change.
     */
    public synchronized int addIfAbsent(String name, String modPack, String builderType, String propertiesJson) {
        String key = makeKey(modPack, name);
        Integer existing = index.get(key);
        if (existing != null) return existing;
        int id = nextId++;
        Registration reg = new Registration(id, name, modPack, builderType, propertiesJson);
        byId.put(id, reg);
        index.put(key, id);
        try {
            save();
        } catch (IOException e) {
            System.err.println("[DynamicBlockRegistry] failed to save new registration: " + e.getMessage());
        }
        return id;
    }

    public Optional<Registration> getById(int id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Integer> getId(String name, String modPack) {
        return Optional.ofNullable(index.get(makeKey(modPack, name)));
    }

    public Optional<String> getBuilderFor(int id) {
        var r = getById(id);
        return r.map(Registration::builderType);
    }

    public Optional<String> getProperties(String name, String modPack) {
        var id = getId(name, modPack);
        return id.flatMap(this::getById).map(Registration::propertiesJson);
    }

    public List<Registration> listAll() {
        return new ArrayList<>(byId.values());
    }
}
