package com.mtsharpgrain.gui;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class Console {
    private final int maxLines;
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private final StringBuilder currentInput = new StringBuilder();
    private Consumer<String> commandHandler = null;
    
    private final PrintStream originalOut;
    private final PrintStream originalErr;

    public Console(int maxLines) {
        this.maxLines = maxLines;
        this.originalOut = System.out;
        this.originalErr = System.err;
        redirectSystemStreams();
    }

    private void redirectSystemStreams() {
        PrintStream outStream = new PrintStream(new TeeOutputStream(false, originalOut));
        PrintStream errStream = new PrintStream(new TeeOutputStream(true, originalErr));
        System.setOut(outStream);
        System.setErr(errStream);
    }

    /** OutputStream that writes to both original stream and captures lines */
    private class TeeOutputStream extends OutputStream {
        private final boolean isError;
        private final PrintStream originalStream;
        private final StringBuilder lineBuffer = new StringBuilder();

        TeeOutputStream(boolean isError, PrintStream originalStream) {
            this.isError = isError;
            this.originalStream = originalStream;
        }

        @Override
        public void write(int b) throws IOException {
            char c = (char) b;
            
            // Always write to original stream immediately
            originalStream.write(b);
            originalStream.flush();
            
            // Buffer for line capture
            if (c == '\n') {
                String line = lineBuffer.toString().trim();
                if (!line.isEmpty()) {
                    if (isError) line = "[ERR] " + line;
                    addLine(line);
                }
                lineBuffer.setLength(0);
            } else {
                lineBuffer.append(c);
            }
        }
    }

    private void addLine(String line) {
        lines.add(line);
        while (lines.size() > maxLines) {
            lines.poll();
        }
    }

    public void println(String line) {
        addLine(line);
    }

    public void submit() {
        String cmd = currentInput.toString().trim();
        if (!cmd.isEmpty()) {
            if (commandHandler != null) {
                commandHandler.accept(cmd);
            }
            currentInput.setLength(0);
        }
    }

    public ConcurrentLinkedQueue<String> getLines() { return lines; }
    public String getCurrentInput() { return currentInput.toString(); }
    public void setCurrentInput(String s) {
        currentInput.setLength(0);
        currentInput.append(s);
    }
    public void setCommandHandler(Consumer<String> handler) {
        this.commandHandler = handler;
    }
}
