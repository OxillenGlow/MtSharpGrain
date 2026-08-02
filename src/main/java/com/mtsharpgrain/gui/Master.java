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
import java.util.List;

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

    public static void tic(IGui gui, ModPackManager modPackManager, Inventory inventory) {

        gui.push(false);
        gui.textFont("Interface/Fonts/Console.fnt");

        gui.textSize(0.02f);
        gui.textColor(mousePressedL ? ColorRGBA.Green : ColorRGBA.Blue);
        gui.textHAlign("left");
        gui.textVAlign("top");
        String path = GameState.guiState;
        
        
        
        if ("play".equals(path)) {
            // Flying/playing: no menu chrome at all, and no mod owns the GuiApi canvas.
            modPackManager.disableAllDrawing();
            inventory.drawMini(gui);
            drawBreakPercentage(gui);
        } else if ("home".equals(path)) {
            modPackManager.disableAllDrawing();
            drawBGandButtons(gui);
            drawViewDistanceSelector(gui);
            drawHomeNav(gui);
            drawSavedModsList(gui, modPackManager);
            inventory.draw(gui);
        } else if ("home/modview".equals(path)) {
            modPackManager.disableAllDrawing();
            gui.textFont("Interface/Fonts/Console.fnt");
            gui.textHAlign("center");
            gui.textVAlign("center");
            gui.textColor(new ColorRGBA(0f, 0f, 0f, 1f)); // 60% opaque black
            gui.textSize(5f); // step below is tuned to roughly match this glyph size

            gui.text("-", 0.5f, 1.9f, null);
            gui.text("-", 0.5f, 0.5f, null);
            drawModTable(gui, modPackManager);
            
        } else if (path.startsWith("home/modview/")) {
            gui.textFont("Interface/Fonts/Console.fnt");
            gui.textHAlign("center");
            gui.textVAlign("center");
            gui.textColor(new ColorRGBA(0f, 0f, 0f, 1f)); // 60% opaque black
            gui.textSize(5f); // step below is tuned to roughly match this glyph size

            gui.text("-", 0.5f, 1.9f, null);
            gui.text("-", 0.5f, 0.5f, null);
            String packName = path.substring("home/modview/".length());
            drawModDetail(gui, modPackManager, packName);
            // The ONLY place a mod's own GuiApi elements are allowed to draw.
            modPackManager.setOnlyDrawing(packName);
        } else {
            // Unknown/stale path (e.g. saved from an old session) — fall back to home.
            GameState.navigateTo("home");
            drawSavedModsList(gui, modPackManager);
            drawViewDistanceSelector(gui);
            drawHomeNav(gui);
            modPackManager.disableAllDrawing();
            inventory.draw(gui);
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

        int slots = 9;
        float slotSpacing = 0.1f;

        for (int i = 1; i <= slots; i++) {
            final int dist = i;
            float xpos = (float) (0.5f + (slotSpacing * (i - (float)slots/2)));

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
        gui.textSize(0.06f);
        // TODO: swap for gui.image(...) once nav-arrow assets are picked from the Hyper pack
        gui.text("Mods >", 0.95f, 0.95f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                GameState.navigateTo("home/modview");
            }
            return true;
        });
        gui.textSize(0.02f);
    }

    // ── home/modview: table of mods with enable/disable ────────────────
    private static void drawModTable(IGui gui, ModPackManager modPackManager) {
        gui.textHAlign("left");
        gui.textVAlign("top");

        gui.textColor(ColorRGBA.White);
        
        gui.textSize(0.04f);
        gui.text("< Back", 0.05f, 0.95f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                GameState.navigateTo("home");
            }
            return true;
        });
        gui.textSize(0.02f);

        gui.textColor(ColorRGBA.White);
        gui.text("Installed Mods:\n*click* on one to veiw it      press[Buttons] to disable", 0.05f, 0.85f, null);
        gui.textSize(0.013f);
        
        float y = 0.76f;
        float rowSpacing = 0.03f;

        for (String packName : modPackManager.getSortedPackNames()) {
            boolean enabled = modPackManager.isEnabled(packName);

            gui.textColor(ColorRGBA.White);
            
            gui.text("- world/my_world/mod/  " + packName, 0.08f, y, (event, arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    GameState.navigateTo("home/modview/" + packName);
                }
                return true;
            });

            gui.textColor(ColorRGBA.Yellow);
            gui.text("[Reload mod]", 0.20f, 0.95f, (event, arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    try {
                        modPackManager.reloadPack(packName);
                    } catch (IOException ex) {
                        System.err.println("Failed to reload pack '" + packName + "': " + ex.getMessage());
                    }
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

            boolean isSaved = modPackManager.isSaved(packName);
            gui.textColor(isSaved ? ColorRGBA.Yellow : ColorRGBA.Gray);
            gui.text(isSaved ? "[Saved]" : "[Save]", 0.65f, y, (event, arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    modPackManager.setSaved(packName, !isSaved);
                }
                return true;
            });

            y -= rowSpacing;
        }
        gui.textSize(0.02f);
    }

    // ── home/modview/<pack>: the only place that pack's own GuiApi draws ──
    private static void drawModDetail(IGui gui, ModPackManager modPackManager, String packName) {
        gui.textHAlign("left");
        gui.textVAlign("top");

        gui.textColor(ColorRGBA.White);

        // High, fixed zIndex so a mod's own GuiApi elements (drawn after this,
        // in a separate push/pop in ModPackManager.draw) can never visually or
        // click-wise cover the only way out of this screen.
        float previousZ = gui.getZIndex();
        gui.zIndex(previousZ + 100f);
        
        gui.textSize(0.04f);
        gui.text("< Back", 0.05f, 0.95f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                GameState.navigateTo("home/modview");
            }
            return true;
        });
        gui.textSize(0.02f);
        gui.zIndex(previousZ);

        gui.textColor(ColorRGBA.White);
        gui.text("Viewing: " + packName, 0.05f, 0.88f, null);

        if (modPackManager.getMod(packName) == null) {
            gui.textColor(ColorRGBA.Red);
            gui.text("This mod pack no longer exists.", 0.05f, 0.80f, (event, arg) -> true);
        }
    }
    
    private static void drawBGandButtons(IGui gui) {
        gui.push(false);

        gui.textFont("Interface/Fonts/Console.fnt");
        gui.textHAlign("center");
        gui.textVAlign("center");
        gui.textColor(new ColorRGBA(0f, 0f, 0f, 1f)); // 60% opaque black
        gui.textSize(5f); // step below is tuned to roughly match this glyph size

        gui.text("-", 0.5f, 1f, null);
        gui.text("-", 0.5f, 0.0f, null);
        // ── Big Play button ──────────────────────────────────────────────────
        gui.textColor(ColorRGBA.White);
        gui.textSize(0.04f);
        gui.text("Press [F] to play", 0.5f, 0.5f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                GameState.guiState = "game";
                
            }
            return true;
        });

        gui.pop();
    }

    // ── home: saved mods list, right side ───────────────────────────────
    private static void drawSavedModsList(IGui gui, ModPackManager modPackManager) {
        gui.textHAlign("right");
        gui.textVAlign("top");

        gui.textColor(ColorRGBA.White);
        gui.textSize(0.025f);
        gui.text("Saved Mods:", 0.95f, 0.85f, null);

        gui.textSize(0.018f);
        float y = 0.80f;
        float rowSpacing = 0.03f;

        List<String> savedNames = modPackManager.getSavedPackNames();
        if (savedNames.isEmpty()) {
            gui.textColor(ColorRGBA.Gray);
            gui.text("(none)", 0.80f, y, null);
        } else {
            for (String packName : savedNames) {
                gui.textColor(ColorRGBA.Yellow);
                gui.text(packName, 0.95f, y, (event, arg) -> {
                    if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                        GameState.navigateTo("home/modview/" + packName);
                    }
                    return true;
                });
                y -= rowSpacing;
            }
        }
    
        gui.textSize(0.02f); // restore the size tic() set before this call
    }

    private static void drawBreakPercentage(IGui gui) {
        if (!(com.mtsharpgrain.WorldAccess.t > 100) || !(com.mtsharpgrain.WorldAccess.t <2)){
            gui.textHAlign("center");
            gui.textVAlign("top");
            gui.textSize(0.04f);
            gui.textColor(ColorRGBA.Black);
            gui.text("Destroying... "+com.mtsharpgrain.WorldAccess.t+"%", 0.5f, 0.4f, null);
        }
    }
}
