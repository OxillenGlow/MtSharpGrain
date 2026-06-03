package test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.lwjgl.system.Configuration;

public class Test {
    public static void main(String[] args) {
        
        Thread renderThread = new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
    System.out.println("A");

    JFrame f = new JFrame("Test");

    System.out.println("B");

    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    System.out.println("C");

    f.setSize(300, 300);

    System.out.println("D");

    f.setVisible(true);

    System.out.println("E");
});
        }, "LWJGL-Render-Thread");
        renderThread.start();
    }
}
