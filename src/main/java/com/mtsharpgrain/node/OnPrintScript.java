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
 */
public class OnPrintScript extends OutputStream {

    private final PrintStream originalOut;
    private final List<OnPrintListener> listeners;
    private StringBuilder buffer;

    /**
     * Guards against re-entrant broadcasts.
     *
     * Problem: if a listener calls System.out.println() itself, that triggers
     * write() → processBuffer() → broadcastToListeners() recursively. Because
     * processBuffer() used to reset the buffer AFTER broadcasting, the recursive
     * write() appended to the still-live buffer, so the inner processBuffer() saw
     * ALL previously-buffered lines again plus the new one, broadcasting each of
     * them a second (or Nth) time — exponential explosion.
     *
     * Fix 1 (buffer): reset buffer BEFORE calling broadcastToListeners() so any
     * recursive write() lands in a fresh buffer and is processed independently.
     *
     * Fix 2 (guard): if a listener's output would re-enter broadcasting (e.g. a
     * "Usage: …" reply is itself mistakenly parsed as a command), skip broadcasting
     * for that inner write so the loop terminates. The output still reaches
     * originalOut so nothing is lost from the console.
     */
    private boolean isBroadcasting = false;

    public OnPrintScript() {
        this.originalOut = System.out;
        this.listeners = new ArrayList<>();
        this.buffer = new StringBuilder();
    }

    /** Attach this OnPrintScript to System.out, replacing the original output stream. */
    public void attach() {
        System.setOut(new PrintStream(this, true));
    }

    /** Restore the original System.out stream. */
    public void detach() {
        System.setOut(originalOut);
    }

    /** Add a listener to respond to print outputs. */
    public void addListener(OnPrintListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Remove a listener. */
    public void removeListener(OnPrintListener listener) {
        listeners.remove(listener);
    }

    /** Remove all listeners. */
    public void clearListeners() {
        listeners.clear();
    }

    @Override
    public void write(int b) throws IOException {
        originalOut.write(b);

        // If we are inside broadcastToListeners, pass through to originalOut only.
        // This prevents a listener's own println() from re-entering the broadcast loop.
        if (isBroadcasting) return;

        buffer.append((char) b);

        if (b == '\n') {
            String line = buffer.toString().trim();
            buffer = new StringBuilder(); // reset BEFORE broadcasting (Fix 1)
            if (!line.isEmpty()) {
                broadcastToListeners(line);
            }
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        originalOut.write(b, off, len);

        // Same guard as write(int): pass through only if we are re-entering.
        if (isBroadcasting) return;

        buffer.append(new String(b, off, len));
        processBuffer();
    }

    /**
     * Extract all complete lines from the buffer and broadcast them.
     *
     * The buffer is reset to the incomplete tail BEFORE broadcasting so that any
     * write() calls that occur inside a listener operate on a fresh buffer and are
     * processed independently rather than being merged with the lines we are about
     * to send.
     */
    private void processBuffer() {
        String content = buffer.toString();
        int lastNewline = content.lastIndexOf('\n');

        if (lastNewline < 0) return; // no complete line yet

        // ── Fix 1: reset buffer BEFORE broadcasting ──────────────────────────────
        // Any recursive write() during broadcast will append to this new, empty
        // buffer instead of the one we are currently processing.
        buffer = new StringBuilder(content.substring(lastNewline + 1));

        String[] lines = content.substring(0, lastNewline).split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                broadcastToListeners(line);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        originalOut.flush();
    }

    /**
     * Broadcast an output line to all registered listeners.
     *
     * The isBroadcasting flag (Fix 2) ensures that if a listener prints something
     * to System.out, that output reaches the console via originalOut but does NOT
     * trigger another round of broadcasting, breaking any potential infinite loop.
     */
    private void broadcastToListeners(String output) {
        isBroadcasting = true;
        try {
            for (OnPrintListener listener : listeners) {
                try {
                    listener.onInput(output);
                } catch (Exception e) {
                    originalOut.println("Error in OnPrintListener: " + e.getMessage());
                    e.printStackTrace(originalOut);
                }
            }
        } finally {
            isBroadcasting = false;
        }
    }

    /**
     * Interface for listening to System.out print events.
     */
    public interface OnPrintListener {
        /**
         * Called when a line is printed to System.out.
         *
         * @param output the printed line (trimmed, without newline)
         */
        void onInput(String output);
    }
}
