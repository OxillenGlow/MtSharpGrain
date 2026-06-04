/*
 * 
 */
package com.mtsharpgrain.gui;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.mtsharpgrain.Main;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.io.IOException;
import javax.swing.JFrame;

/**
 *
 * @author OxillenGlow
 */
public class SwingStarter {
    public static void main(String[] args) throws IOException {
        //I am seeing if embedding in jFrame works
        System.out.println("0");
        
        System.out.println("0");
        javax.swing.JFrame frame = new javax.swing.JFrame("Test");
        System.out.println("1");
        
        System.out.println("2");
        AppSettings settings = new AppSettings(true);
        settings.setWidth(1280);
        settings.setHeight(720);
        System.out.println("3");
        
        System.out.println("6");
        
        System.out.println("8");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        System.out.println("9");
        
        System.out.println("10");
        frame.pack();
        System.out.println("11");
        frame.setSize(1280, 720);
        System.out.println("12");
        frame.setVisible(true);
    
        
    }
}
