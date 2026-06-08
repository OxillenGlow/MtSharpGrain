package com.mtsharpgrain.node;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * OnPrintScript monitors System.out.println() calls and broadcasts them to registered listeners.
 * This allows the visual scripting system to respond to print commands from the game engine.
 * 
 * Usage:
 *   OnPrintScript scriptMonitor = new OnPrintScript();
 *   scriptMonitor.attach();  // Replaces System.out
 *   scriptMonitor.addListener(myListener);
 * Note that: this class is COMPLETELY generate by copilot/claude :p
 * I'm lazy sometimes. hopefully it will run well.
 */
public class OnPrintScript extends OutputStream {
    
    private PrintStream originalOut;
    private List<OnPrintListener> listeners;
    private StringBuilder buffer;
    
    public OnPrintScript() {
        this.originalOut = System.out;
        this.listeners = new ArrayList<>();
        this.buffer = new StringBuilder();
    }
    
    /**
     * Attach this OnPrintScript to System.out, replacing the original output stream
     */
    public void attach() {
        System.setOut(new PrintStream(this, true));
    }
    
    /**
     * Restore the original System.out stream
     */
    public void detach() {
        System.setOut(originalOut);
    }
    
    /**
     * Add a listener to respond to print outputs
     */
    public void addListener(OnPrintListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a listener
     */
    public void removeListener(OnPrintListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Remove all listeners
     */
    public void clearListeners() {
        listeners.clear();
    }
    
    @Override
    public void write(int b) throws IOException {
        // Write to original output
        originalOut.write(b);
        
        // Buffer the character
        buffer.append((char) b);
        
        // If we hit a newline, process the complete line
        if (b == '\n') {
            String line = buffer.toString().trim();
            buffer = new StringBuilder();
            
            if (!line.isEmpty()) {
                broadcastToListeners(line);
            }
        }
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // Write to original output
        originalOut.write(b, off, len);
        
        // Buffer the string
        String str = new String(b, off, len);
        buffer.append(str);
        
        // Process any complete lines
        processBuffer();
    }
    
    /**
     * Process the buffer looking for complete lines
     */
    private void processBuffer() {
        String content = buffer.toString();
        int lastNewline = content.lastIndexOf('\n');
        
        if (lastNewline >= 0) {
            // Process all complete lines
            String[] lines = content.substring(0, lastNewline).split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    broadcastToListeners(line);
                }
            }
            
            // Keep the incomplete part
            buffer = new StringBuilder(content.substring(lastNewline + 1));
        }
    }
    
    @Override
    public void flush() throws IOException {
        originalOut.flush();
    }
    
    /**
     * Broadcast the output line to all registered listeners
     */
    private void broadcastToListeners(String output) {
        for (OnPrintListener listener : listeners) {
            try {
                listener.onInput(output);
            } catch (Exception e) {
                // Print to original stream to avoid infinite recursion
                originalOut.println("Error in OnPrintListener: " + e.getMessage());
                e.printStackTrace(originalOut);
            }
        }
    }
    
    /**
     * Interface for listening to System.out print events
     */
    public interface OnPrintListener {
        /**
         * Called when a line is printed to System.out
         * @param output the printed line (without newline)
         */
        void onInput(String output);
    }
}
