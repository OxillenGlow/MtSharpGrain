package com.mtsharpgrain.gui;

import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.math.ColorRGBA;
import com.mtsharpgrain.node.BlockRegistry;
import com.mtsharpgrain.node.BlockRegistry.BlockDef;
import com.mtsharpgrain.Main;
import com.mtsharpgrain.js.mainthread.ModPackManager;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author oxillenglow
 */
public class Master {

    static boolean mouseHover = false;
    static boolean mousePressedL = false;

    // amount of block types (0..x inclusive => x+1 buttons)
    public static int x = 10;
    // currently selected block type
    public static int blockType = 0;

    // lower bound of the currently displayed page (10 numbers shown: pageStart..pageStart+9)
    public static int pageStart = 1;

    public static void tic(IGui gui, ModPackManager modPackManager) {

        gui.push(false);
        gui.textFont("Interface/Fonts/Default.fnt");

        gui.textSize(0.02f);
        gui.textColor(mousePressedL ? ColorRGBA.Green : ColorRGBA.Blue);
        gui.textHAlign("left");
        gui.textVAlign("top");
        String path = GameState.guiState;

        if ("play".equals(path)) {
            // Flying/playing: no menu chrome at all, and no mod owns the GuiApi canvas.
            modPackManager.disableAllDrawing();
        } else if ("home".equals(path)) {
            drawBlockTypeSelector(gui);
            drawViewDistanceSelector(gui);
            drawHomeNav(gui);
            modPackManager.disableAllDrawing();
        } else if ("home/modview".equals(path)) {
            drawModTable(gui, modPackManager);
            modPackManager.disableAllDrawing();
        } else if (path.startsWith("home/modview/")) {
            String packName = path.substring("home/modview/".length());
            drawModDetail(gui, modPackManager, packName);
            // The ONLY place a mod's own GuiApi elements are allowed to draw.
            modPackManager.setOnlyDrawing(packName);
        } else {
            // Unknown/stale path (e.g. saved from an old session) — fall back to home.
            GameState.navigateTo("home");
            drawBlockTypeSelector(gui);
            drawViewDistanceSelector(gui);
            drawHomeNav(gui);
            modPackManager.disableAllDrawing();
        }

        gui.pop();
    }

    private static void drawBlockTypeSelector(IGui gui) {
        gui.textHAlign("center");
        gui.textVAlign("bottom");

        float y = 0f; // bottom of screen

        // status/help line, sits just above the button row
        gui.textColor(ColorRGBA.White);
        gui.text("< -10 / +10 > to browse  |  Selected: [" + blockType + "]", 0.5f, y + 0.045f, null);
        
        float leftMargin = 0.05f;
        float rightMargin = 0.95f;

        // total slots: "< -10" + 10 numbers + "+10 >"
        int totalSlots = 12;
        float slotSpacing = (rightMargin - leftMargin) / (totalSlots - 1);

        // "< -10" clickable, shifts page down by 10
        gui.textColor(ColorRGBA.Blue);
        gui.text("< -10", leftMargin, y, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                pageStart -= 10;
            }
            return true;
        });

        // 10 number buttons for the current page
        for (int i = 0; i < 10; i++) {
            final int blockIndex = pageStart + i;
            float xpos = leftMargin + slotSpacing * (i + 1);

            BlockDef def = BlockRegistry.get(blockIndex);
            ColorRGBA color;
            try {
                color = def.diffuse();
            }catch(Exception e) {color = ColorRGBA.Black;}
            
            gui.textColor(color);

            String label = "[" + blockIndex + "]";
            gui.text(label, xpos, y, (event, arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    blockType = blockIndex;
                }
                return true;
            });
        }

        // "+10 >" clickable, shifts page up by 10
        gui.textColor(ColorRGBA.Blue);
        gui.text("+10 >", rightMargin, y, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                pageStart += 10;
            }
            return true;
        });
    }
    
    private static void drawViewDistanceSelector(IGui gui) {
        gui.textHAlign("center");
        gui.textVAlign("bottom");

        // sits above the block-type row (which occupies y .. y+0.045)
        float y = 0.09f;

        gui.textColor(ColorRGBA.White);
        gui.text("View Distance: [" + Main.VIEW_DISTANCE + "]", 0.5f, y + 0.045f, null);

        float leftMargin = 0.35f;
        float rightMargin = 0.65f;
        int slots = 5; // 1..5
        float slotSpacing = (rightMargin - leftMargin) / (slots - 1);

        for (int i = 1; i <= slots; i++) {
            final int dist = i;
            float xpos = leftMargin + slotSpacing * (i - 1);

            gui.textColor(dist == Main.VIEW_DISTANCE ? ColorRGBA.Green : ColorRGBA.Blue);
            gui.text("[" + dist + "]", xpos, y, (event, arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    Main.VIEW_DISTANCE = dist;
                }
                return true;
            });
        }
    }

    // ── Navigation: home -> home/modview ────────────────────────────────
    private static void drawHomeNav(IGui gui) {
        gui.textHAlign("right");
        gui.textVAlign("top");
        gui.textColor(ColorRGBA.White);
        // TODO: swap for gui.image(...) once nav-arrow assets are picked from the Hyper pack
        gui.text("Mods >", 0.95f, 0.95f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                GameState.navigateTo("home/modview");
            }
            return true;
        });
    }

    // ── home/modview: table of mods with enable/disable ────────────────
    private static void drawModTable(IGui gui, ModPackManager modPackManager) {
        gui.textHAlign("left");
        gui.textVAlign("top");

        gui.textColor(ColorRGBA.White);
        gui.text("< Back", 0.05f, 0.95f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                GameState.navigateTo("home");
            }
            return true;
        });

        gui.textColor(ColorRGBA.White);
        gui.text("Installed Mods", 0.05f, 0.88f, null);

        float y = 0.80f;
        float rowSpacing = 0.05f;

        for (String packName : modPackManager.getSortedPackNames()) {
            boolean enabled = modPackManager.isEnabled(packName);

            gui.textColor(ColorRGBA.White);
            gui.text(packName, 0.08f, y, (event, arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    GameState.navigateTo("home/modview/" + packName);
                }
                return true;
            });

            gui.textColor(enabled ? ColorRGBA.Green : ColorRGBA.Red);
            gui.text(enabled ? "[Enabled]" : "[Disabled]", 0.45f, y, (event, arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    modPackManager.setEnabled(packName, !enabled);
                }
                return true;
            });

            y -= rowSpacing;
        }
    }

    // ── home/modview/<pack>: the only place that pack's own GuiApi draws ──
    private static void drawModDetail(IGui gui, ModPackManager modPackManager, String packName) {
        gui.textHAlign("left");
        gui.textVAlign("top");

        gui.textColor(ColorRGBA.White);
        gui.text("< Back", 0.05f, 0.95f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                GameState.navigateTo("home/modview");
            }
            return true;
        });

        gui.textColor(ColorRGBA.White);
        gui.text("Viewing: " + packName, 0.05f, 0.88f, null);

        if (modPackManager.getMod(packName) == null) {
            gui.textColor(ColorRGBA.Red);
            gui.text("This mod pack no longer exists.", 0.05f, 0.80f, (event, arg) -> true);
        }
    }
}
