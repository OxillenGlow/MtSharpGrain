package com.mtsharpgrain.gui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class Console {
    private final int maxLines;
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private final StringBuilder currentInput = new StringBuilder();
    private Consumer<String> commandHandler = null;

    public Console(int maxLines) {
        this.maxLines = maxLines;
        redirectSystemStreams();
    }

    /** Redirect System.out and System.err to our own PrintStreams */
    private void redirectSystemStreams() {
        PrintStream outStream = new PrintStream(new ConsoleOutputStream(false));
        PrintStream errStream = new PrintStream(new ConsoleOutputStream(true));
        System.setOut(outStream);
        System.setErr(errStream);
    }

    /** Inner stream that captures each line and adds it to the queue */
    private class ConsoleOutputStream extends ByteArrayOutputStream {
        private final boolean isError;

        ConsoleOutputStream(boolean isError) {
            this.isError = isError;
        }

        @Override
        public void flush() {
            String line = toString().trim();
            if (!line.isEmpty()) {
                // Optionally prepend "[ERR] " for error lines
                if (isError) line = "[ERR] " + line;
                addLine(line);
            }
            reset();
        }
    }

    /** Add a line to the history, trimming if needed */
    private void addLine(String line) {
        lines.add(line);
        while (lines.size() > maxLines) {
            lines.poll();
        }
    }

    /** Manually add a line (e.g., for command echo) */
    public void println(String line) {
        addLine(line);
    }

    /** Submit the current input to the command handler */
    public void submit() {
        String cmd = currentInput.toString().trim();
        if (!cmd.isEmpty()) {
            System.out.println("> " + cmd);      // echo the command
            if (commandHandler != null) {
                commandHandler.accept(cmd);
            }
            currentInput.setLength(0);
        }
    }

    // Getters and setters
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