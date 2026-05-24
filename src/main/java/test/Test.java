package test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Test {
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            System.out.println(
    java.lang.management.ManagementFactory
        .getRuntimeMXBean()
        .getInputArguments()
);
            System.out.println("1");
            JFrame f = new JFrame("Test");
            System.out.println("1");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            System.out.println("1");
            f.setSize(300, 300);
            System.out.println("1");
            f.setLocationRelativeTo(null);
            System.out.println("1");
            f.setVisible(true);
            System.out.println("1");
            System.out.println("VISIBLE");
        });
    }
}