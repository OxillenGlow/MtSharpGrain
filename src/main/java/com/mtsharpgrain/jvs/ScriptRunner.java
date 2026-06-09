package com.mtsharpgrain.jvs;

import com.jvisualscripting.event.StartEventNode;
import java.io.File;
import com.jvisualscripting.Engine;
import com.jvisualscripting.EventGraph;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author oxillenglow
 */
public class ScriptRunner{
    public static void loadAndExecuteVisualScript() {
        try {
            // Create engine instance for visual scripting
            Engine visualEngine = Engine.getDefault();
            
            // Path to your .jvsz file - modify this path as needed
            File jvszFile = new File("scripts/CORE/simpleInit.jvsz");
            // for setting variables
            Map<String, String> keyValue = new HashMap<>();
            
            // Check if file exists before attempting to load
            if (!jvszFile.exists()) {
                System.out.println("Visual script file not found at: " + jvszFile.getAbsolutePath());
                return;
            }
            
            // Load the EventGraph from the .jvsz file
            EventGraph graph = new EventGraph();
            graph.load(jvszFile, visualEngine);
            for (String key : keyValue.keySet()) {
                 graph.putParameter(key,keyValue.get(key));
            }
            graph.start();
        } catch (Exception e) {
            System.out.println("Error executing visual script: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
