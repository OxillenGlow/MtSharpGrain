package com.mtsharpgrain.gui;

import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.math.ColorRGBA;
import com.mtsharpgrain.GameState;
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
        }

        gui.pop();
    }

    private static void drawBlockTypeSelector(IGui gui) {
        gui.textColor(ColorRGBA.Blue);
        gui.textHAlign("center");
        gui.textVAlign("bottom");

        float y = 0f; // bottom of screen

        // "<" and "[x]" buttons share the row, evenly spread between them
        float leftMargin = 0.05f;
        float rightMargin = 0.95f;

        // total slots: "<" + (x+1) numbers + ">"
        int totalSlots = x + 3;
        float slotSpacing = (rightMargin - leftMargin) / (totalSlots - 1);

        // "<" decorative, leftmost
        gui.text("<", leftMargin, y, null);

        // number buttons [0]..[x]
        for (int i = 0; i <= x; i++) {
            final int blockIndex = i;
            float xpos = leftMargin + slotSpacing * (i + 1);
            String label = "[" + blockIndex + "]";

            gui.text(label, xpos, y, (var event, var arg) -> {
                if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) {
                    blockType = blockIndex;
                }
                return true;
            });
        }

        // ">" decorative, rightmost
        gui.text(">", rightMargin, y, null);
    }

}
