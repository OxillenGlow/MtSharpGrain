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

    /**
     * Registration expanded to include per-block color/shininess parameters so
     * mod blocks can provide their own material colours.
     *
     * Fields:
     *  - id, name, modPack, builderType, propertiesJson : existing values
     *  - dr,dg,db,da : diffuse RGBA (0..1)
     *  - sr,sg,sb,sa : specular RGBA (0..1)
     *  - shininess    : Phong shininess
     * @param dr diffuse (real color) values
     * @param sr specular (shiny reflection) values
     */
    public record Registration(int id, String name, String modPack, String builderType, String propertiesJson,
                               float dr, float dg, float db, float da,
                               float sr, float sg, float sb, float sa,
                               float shininess) {}

    private static volatile DynamicBlockRegistry INSTANCE;

    private final Path file;
    private final Map<Integer, Registration> byId = new ConcurrentHashMap<>();
    private final Map<String, Integer> index = new ConcurrentHashMap<>();
    private int nextId;

    private static final int BASE_DYNAMIC_ID = -1;// Goes from -1 to -n for n in mod blocks

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
            DocumentBuilder docbuilder = dbf.newDocumentBuilder();
            Document doc = docbuilder.parse(is);
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
                // Read optional color/shininess attributes with safe defaults
                float dr = el.hasAttribute("dr") ? Float.parseFloat(el.getAttribute("dr")) : 1.0f;
                float dg = el.hasAttribute("dg") ? Float.parseFloat(el.getAttribute("dg")) : 1.0f;
                float db = el.hasAttribute("db") ? Float.parseFloat(el.getAttribute("db")) : 1.0f;
                float da = el.hasAttribute("da") ? Float.parseFloat(el.getAttribute("da")) : 1.0f;
                float sr = el.hasAttribute("sr") ? Float.parseFloat(el.getAttribute("sr")) : 0.0f;
                float sg = el.hasAttribute("sg") ? Float.parseFloat(el.getAttribute("sg")) : 0.0f;
                float sb = el.hasAttribute("sb") ? Float.parseFloat(el.getAttribute("sb")) : 0.0f;
                float sa = el.hasAttribute("sa") ? Float.parseFloat(el.getAttribute("sa")) : 1.0f;
                float shininess = el.hasAttribute("shininess") ? Float.parseFloat(el.getAttribute("shininess")) : 0.0f;

                Registration reg = new Registration(id, name, modPack, builder, props,
                        dr, dg, db, da, sr, sg, sb, sa, shininess);
                byId.put(id, reg);
                index.put(makeKey(modPack, name), id);
                if (id <= nextId) nextId = id - 1;
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
                // Persist colour and shininess attributes for dynamic blocks
                block.setAttribute("dr", Float.toString(reg.dr()));
                block.setAttribute("dg", Float.toString(reg.dg()));
                block.setAttribute("db", Float.toString(reg.db()));
                block.setAttribute("da", Float.toString(reg.da()));
                block.setAttribute("sr", Float.toString(reg.sr()));
                block.setAttribute("sg", Float.toString(reg.sg()));
                block.setAttribute("sb", Float.toString(reg.sb()));
                block.setAttribute("sa", Float.toString(reg.sa()));
                block.setAttribute("shininess", Float.toString(reg.shininess()));
                
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
     * Register a new block if absent. New overload accepts explicit colour
     * parameters. The old signature is retained for compatibility.
     */
    public synchronized int addIfAbsent(String name, String modPack, String builderType, String propertiesJson) {
        // delegate to full form with default colours (opaque white diffuse, no specular, shininess 0)
        return addIfAbsent(name, modPack, builderType, propertiesJson, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 1f, 0f);
    }

    public synchronized int addIfAbsent(String name, String modPack, String builderType, String propertiesJson,
                                        float dr, float dg, float db, float da,
                                       float sr, float sg, float sb, float sa,
                                        float shininess) {
        String key = makeKey(modPack, name);
        Integer existing = index.get(key);
        if (existing != null) return existing;
        int id = nextId--;
        Registration reg = new Registration(id, name, modPack, builderType, propertiesJson, dr, dg, db, da, sr, sg, sb, sa, shininess);
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
