package com.tools;

import org.lwjgl.glfw.GLFW;

public class ClipboardUtil {
    private static final long WINDOW_HANDLE = 0; 

    public static void copyToClipboard(String text) {
        // Use the method signature you provided
        GLFW.glfwSetClipboardString(WINDOW_HANDLE, text);
    }

    public static String pasteFromClipboard() {
        // glfwGetClipboardString also requires the window handle
        String text = GLFW.glfwGetClipboardString(WINDOW_HANDLE);
        return (text != null) ? text : "";
    }
}

