package com.mtsharpgrain.gui;

import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.math.ColorRGBA;
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

    public static void tic(IGui gui) {
        
        gui.push(false);
        gui.textFont("Interface/Fonts/Default.fnt");

        gui.textSize(0.02f);
        gui.textColor(mousePressedL?ColorRGBA.Green:ColorRGBA.Blue);
        gui.textHAlign("left");
        gui.textVAlign("top");
        float spacing=0.03f;
        float line=1;
        //gui.text("Open jVisualScript Editor",0f,line,(var event,var arg)->{
        //    if(event==IGuiMouseEvent.MOUSE_IN){
        //        mouseHover=true;
        //    }else if(event==IGuiMouseEvent.MOUSE_OUT){
        //         mouseHover=false;
        //    }
        //    if(event==IGuiMouseEvent.MOUSE_PRESSED_LEFT){
        //        mousePressedL = true;
        //        try {
        //            var jar = com.tools.AssetConverter.getAssetAsFile("/jVisualScripting-editor-1.4.jar");
        //            new ProcessBuilder("java", "-jar", jar.getAbsolutePath())
        //                .inheritIO()
        //                .start();
        //        } catch (IOException ex) {
        //            Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
        //        }
        //    }else if(event==IGuiMouseEvent.MOUSE_RELEASED_LEFT){
        //        mousePressedL=false;
        //    }
        //    return true;
        //});
        gui.text("Line1",0f,line,(var event,var arg)->{
            if(event==IGuiMouseEvent.MOUSE_IN){
                mouseHover=true;
            }else if(event==IGuiMouseEvent.MOUSE_OUT){
                mouseHover=false;
            }
            if(event==IGuiMouseEvent.MOUSE_PRESSED_LEFT){
                mousePressedL = true;
            }else if(event==IGuiMouseEvent.MOUSE_RELEASED_LEFT){
                mousePressedL=false;
            }
            return true;
        });
            
        gui.pop();     
    }
    
}
