package com.mtsharpgrain.gui;

import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.math.ColorRGBA;
import com.mtsharpgrain.node.BlockRegistry;
import com.mtsharpgrain.node.BlockRegistry.BlockDef;
import com.mtsharpgrain.Main;
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

    public static void tic(IGui gui) {

        gui.push(false);
        gui.textFont("Interface/Fonts/Default.fnt");

        gui.textSize(0.02f);
        gui.textColor(mousePressedL ? ColorRGBA.Green : ColorRGBA.Blue);
        gui.textHAlign("left");
        gui.textVAlign("top");
        float spacing = 0.03f;
        float line = 1;

        if (!GameState.isOkPlace()) {
            drawBlockTypeSelector(gui);
            drawViewDistanceSelector(gui);
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
}
