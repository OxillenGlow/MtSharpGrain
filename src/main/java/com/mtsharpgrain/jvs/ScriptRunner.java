package com.mtsharpgrain.jvs;

import com.jvisualscripting.event.StartEventNode;
import java.io.File;
import com.jvisualscripting.Engine;
import com.jvisualscripting.EventGraph;

public static class ScriptRunner{
    private void loadAndExecuteVisualScript() {
        try {
            // Create engine instance for visual scripting
            Engine visualEngine = new Engine();
            
            // Path to your .jvsz file - modify this path as needed
            File jvszFile = new File("scripts/default.jvsz");
            
            // Check if file exists before attempting to load
            if (!jvszFile.exists()) {
                System.out.println("Visual script file not found at: " + jvszFile.getAbsolutePath());
                return;
            }
            
            // Load the EventGraph from the .jvsz file
            EventGraph graph = new EventGraph();
            graph.load(jvszFile, visualEngine);
            
            // Get the first StartEventNode and execute it
            StartEventNode startEvent = graph.getFirstStartEvent();
            if (startEvent != null) {
                System.out.println("Executing visual script: " + jvszFile.getName());
                startEvent.execute();
                System.out.println("Visual script execution completed.");
            } else {
                System.out.println("No start event found in visual script: " + jvszFile.getName());
            }
        } catch (Exception e) {
            System.out.println("Error executing visual script: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
