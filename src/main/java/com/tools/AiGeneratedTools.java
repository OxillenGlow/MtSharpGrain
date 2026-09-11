/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
}
