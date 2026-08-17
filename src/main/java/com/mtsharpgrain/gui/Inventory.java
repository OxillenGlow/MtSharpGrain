package com.mtsharpgrain.gui;

import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.math.ColorRGBA;
import com.mtsharpgrain.node.BlockRegistry;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Java-side player inventory. Tracks how many of each block type the player
 * is carrying (max {@link #MAX_TYPES} distinct types - space is limited by
 * TYPE count, not total amount held).
 *
 * Gates {@link com.mtsharpgrain.WorldAccess#setBlockAt} (NOT forceSetBlockAt -
 * that's intentionally left alone, same convention as the JS BlockChangeRegistry):
 *   - breaking a block (something solid -> air/reserved) picks it up, but only
 *     if we already carry that type or there's room for a new one.
 *   - placing a block (air/reserved -> something solid) spends one, but only
 *     if we have at least one.
 *
 * Same in-memory-buffer-then-flush-on-close pattern as DataApi: mutations
 * only touch the in-memory map, onClose() is the only thing that writes to
 * disk (worlds/<world>/inventory.xml).
 *
 * Like every other JS-bound registry in this project (NodeRegistry, GuiApi,
 * TickRegistry...), this is NOT thread-safe on purpose - every call (from
 * Java or from JS) is expected to happen on the render/main thread.
 */
public class Inventory {

    public static final int MAX_TYPES = 20;

    // Human-readable names, index == block id. Falls back to "Block <id>"
    // past the end - mirrors 01_blocknames.js's BLOCK_NAMES_POS.
    private static final String[] NAMES = {
        "Air", "Reserved", "Stone", "Dirt", "Grass",
        "Crystal Ore", "Ice Sludge", "Silicon", "Sulfur", "Metal Block", "Glass"
    };

    // Icon paths for each block id (0..10). These point into the assets root/cc0 folder.
    // Filenames provided by the user; prefixed with /cc0/ to reference the cc0 asset pack.
    private static final String[] ICONS = {
        "/cc0/clouds.png",                // 0 Air
        "/cc0/clouds.png",                // 1 Reserved
        "/cc0/dirtCaveRockLarge.png",     // 2 Stone
        "/cc0/dirt.png",                  // 3 Dirt
        "/cc0/weat_stage1.png",           // 4 Grass
        "/cc0/ore_diamond.png",           // 5 Crystal Ore
        "/cc0/dirtCaveBottom.png",        // 6 Ice Sludge
        "/cc0/platformPack_tile054.png",  // 7 Silicon
        "/cc0/ore_sulpher.png",           // 8 Sulfur
        "/cc0/metalBlock.png",            // 9 Metal Block
        "/cc0/glass.png"                  // 10 Glass
    };

    private final LinkedHashMap<Integer, Integer> counts = new LinkedHashMap<>();
    private final Path saveFile;

    public Inventory(String worldFolder) {
        this.saveFile = Paths.get(worldFolder, "inventory.xml");
        load();
    }

    private void load() {
        if (!Files.exists(saveFile)) return;
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(saveFile)) {
            props.loadFromXML(is);
        } catch (IOException e) {
            System.err.println("[Inventory] failed to load " + saveFile + ": " + e.getMessage());
            return;
        }
        for (String key : props.stringPropertyNames()) {
            try {
                int id = Integer.parseInt(key);
                int amount = Integer.parseInt(props.getProperty(key));
                if (amount > 0) counts.put(id, amount);
            } catch (NumberFormatException ignore) {
                // corrupted/foreign key - skip it rather than crash the whole load
            }
        }
    }

    /** Flushes the in-memory buffer to disk. Java-only - call once at app shutdown. */
    public void onClose() {
        Properties props = new Properties();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            props.setProperty(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        Path temp = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(saveFile.getParent());
            try (OutputStream os = Files.newOutputStream(temp)) {
                props.storeToXML(os, "MtSharpGrain player inventory");
            }
            Files.move(temp, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[Inventory] failed to save " + saveFile + ": " + e.getMessage());
        }
    }

    // ── Block-change interception (called from WorldAccess.setBlockAt) ─────

    /**
     * @param existingBlockId what's currently at the target cell
     * @param newBlockId      what the caller is trying to set it to
     * @return false to reject the whole change (WorldAccess.setBlockAt should
     *         no-op if this returns false)
     */
    public boolean handleBlockChange(int existingBlockId, int newBlockId) {
        boolean isBreak = existingBlockId != 0 && existingBlockId != 1
                && (newBlockId == 0 || newBlockId == 1);
        boolean isPlace = (existingBlockId == 0 || existingBlockId == 1)
                && newBlockId != 0 && newBlockId != 1;

        if (isBreak) return pickup(existingBlockId);
        if (isPlace) return spend(newBlockId);
        return true; // not a pickup/spend transaction (e.g. overwriting one solid block with another) - let it through
    }

    private boolean pickup(int blockId) {
        Integer have = counts.get(blockId);
        if (have == null) {
            if (counts.size() >= MAX_TYPES) return false; // no room for a new TYPE
            counts.put(blockId, 1);
        } else {
            counts.put(blockId, have + 1);
        }
        return true;
    }

    private boolean spend(int blockId) {
        Integer have = counts.get(blockId);
        if (have == null || have <= 0) return false; // nothing to place
        if (have == 1) counts.remove(blockId);
        else counts.put(blockId, have - 1);
        return true;
    }

    // ── JS-facing API (bound as the "Inventory" global, same wiring as Block.*) ──

    @HostAccess.Export
    public boolean addItem(int blockId, int amount) {
        if (amount <= 0) return false;
        Integer have = counts.get(blockId);
        if (have == null) {
            if (counts.size() >= MAX_TYPES) return false;
            counts.put(blockId, amount);
        } else {
            counts.put(blockId, have + amount);
        }
        return true;
    }

    @HostAccess.Export
    public boolean removeItem(int blockId, int amount) {
        if (amount <= 0) return false;
        Integer have = counts.get(blockId);
        if (have == null || have < amount) return false;
        if (have.equals(amount)) counts.remove(blockId);
        else counts.put(blockId, have - amount);
        return true;
    }

    @HostAccess.Export
    public int getAmount(int blockId) {
        Integer have = counts.get(blockId);
        return have == null ? 0 : have;
    }

    // ── Selection ────────────────────────────────────────────────────────

    public void select(int blockId) {
        Master.blockType = blockId;
    }

    /** -1 if nothing is selected. */
    public int getSelectedItem() {
        return Master.blockType;
    }

    // ── GUI ────────────────────────────────────────────────────────────[...]

    private List<Map.Entry<Integer, Integer>> sortedEntries() {
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue()); // most-held first
        return list;
    }

    private static String blockName(int id) {
        return (id >= 0 && id < NAMES.length) ? NAMES[id] : ("Block " + id);
    }

    /** Draws the list on the left side of the home screen. Click a row to select it. */
    public void draw(IGui gui) {
        gui.textHAlign("left");
        gui.textVAlign("top");

        gui.textColor(ColorRGBA.White);
        gui.textSize(0.025f);
        gui.text("Inventory:", 0.05f, 0.90f, null);

        gui.textSize(0.016f);
        float y = 0.85f;
        float rowSpacing = 0.035f;

        List<Map.Entry<Integer, Integer>> entries = sortedEntries();
        if (entries.isEmpty()) {
            gui.textColor(ColorRGBA.Gray);
            gui.text("(empty)", 0.05f, y, null);
        } else {
            // prepare small icon drawing parameters: left-aligned, center vertical
            gui.imageSize(0.02f, 0.02f).imageAlpha(true).imageColor(ColorRGBA.White)
               .imageHAlign("left").imageVAlign("center");

            for (Map.Entry<Integer, Integer> entry : entries) {
                final int blockId = entry.getKey();
                final int amount = entry.getValue();
                boolean isSelected = blockId == Master.blockType;

                ColorRGBA nameColor;
                if (isSelected) {
                    nameColor = ColorRGBA.Green;
                } else {
                    BlockRegistry.BlockDef def = BlockRegistry.get(blockId);
                    nameColor = def != null ? def.diffuse() : ColorRGBA.White;
                }

                // draw icon (if we have one for this id) to the left of the name
                // Draw the image non-persistent so it is recreated/removed each frame
                if (blockId >= 0 && blockId < ICONS.length) {
                    String iconPath = ICONS[blockId];
                    if (iconPath != null && !iconPath.isEmpty()) {
                        try {
                            // place icon slightly left of the text (text x=0.05f)
                            gui.image(iconPath, 0.02f, y, false);
                        } catch (Exception e) {
 
                        // silently ignore missing assets; fall back to text only
                        }
                    }
                }
                gui.textColor(nameColor);
                gui.text(blockName(blockId), 0.05f, y, (event, arg) -> {
                    if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                        select(blockId);
                        System.out.println("Selected "+blockId);
                    }
                    return true;
                });

                // "subscript" amount, slightly right and below the thumbnail
                gui.textColor(ColorRGBA.Gray);
                gui.textSize(0.018f);
                gui.text("x" + amount, 0.03f, y - 0.014f, null);
                gui.textSize(0.016f);

                y -= rowSpacing;
            }
        }

        gui.textSize(0.02f); // restore the size tic() had set before this call
    }

    /** Draws a compact inventory HUD while the player is in the world. */
    public void drawMini(IGui gui) {
        gui.textHAlign("left");
        gui.textVAlign("bottom");
        gui.textColor(ColorRGBA.White);
        gui.textSize(0.016f);

        int selected = Master.blockType;
        if (Master.blockType >= 0) {
            gui.text(blockName(selected) + " x" + getAmount(selected), 0.05f, 0.08f, null);
        } else {
            gui.text("No block selected", 0.05f, 0.08f, null);
        }

        gui.textSize(0.02f);
    }
}
