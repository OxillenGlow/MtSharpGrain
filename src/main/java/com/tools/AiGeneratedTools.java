package com.tools;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author the internet (that the ai trained on)
 */
public class AiGeneratedTools {
    public static void openWebpage(String url) {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;

        if (os.contains("win")) {
            // Windows requires 'cmd /c start' to launch the default browser
            pb = new ProcessBuilder("cmd", "/c", "start", url);
        } else if (os.contains("mac")) {
            // macOS uses the 'open' command
            pb = new ProcessBuilder("open", url);
        } else if (os.contains("nix") || os.contains("nux")) {
            // Linux uses 'xdg-open'
            pb = new ProcessBuilder("xdg-open", url);
        } else {
            System.out.println("Unsupported operating system.");
            return;
        }

        try {
            pb.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static String getPageContent(String urlString) throws IOException, InterruptedException {
        // Create the HTTP client
        HttpClient client = HttpClient.newHttpClient();

        // Build the request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .build();

        // Send the request and receive the response as a string
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Return the body of the response (the HTML)
        return response.body();
    }
    public static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir); // so Finder/Explorer always has something to open
            //if (Desktop.isDesktopSupported() && !System.getProperty("os.name").toLowerCase().contains("mac") && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {// LWJGL doesent like AWT on macs :( (not sure if it likes awt on any other os though but i havent heard anything about that yet)
            //    Desktop.getDesktop().open(dir.toFile());
            //    return;
            //}
        } catch (Exception ignored) {}
        var os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        var path = dir.toAbsolutePath().toString();
        if (os.contains("win")) {
            pb = new ProcessBuilder("explorer", path);
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("open", path);
        } else {
            pb = new ProcessBuilder("xdg-open", path);
        }
        try {
            pb.start();
        } catch (IOException e) {
            System.err.println("Could not open folder: " + path + " — " + e.getMessage());
        }
    }
}
