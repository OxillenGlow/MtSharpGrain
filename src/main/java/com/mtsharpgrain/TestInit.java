
package com.mtsharpgrain;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.shape.Box;
import com.jme3.scene.Node;
import com.jme3.input.FlyByCamera;
import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
class TestInit {
    public static Object init(Node rootNode, FlyByCamera flyCam, AssetManager assetManager, InputManager inputManager){
        
        
    
        AmbientLight al = new AmbientLight();
        al.setColor(new ColorRGBA(0.2f, 0.2f, 0.2f, 1.0f)); // Max brightness
        rootNode.addLight(al);
        /** A white, directional light source */ 
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection((new Vector3f(-0.5f, -0.8f, -0.3f)).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        rootNode.addLight(sun);
        /* A colored lit cube. Needs light source! */ 
        Box boxMesh = new Box(1f,1f,1f); 
        Geometry boxGeo = new Geometry("Colored Box", boxMesh); 
        Material boxMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md"); 
        boxMat.setBoolean("UseMaterialColors", true); 
        boxMat.setColor("Ambient", ColorRGBA.Green); 
        boxMat.setColor("Diffuse", ColorRGBA.Green); 
        boxGeo.setMaterial(boxMat); 
        rootNode.attachChild(boxGeo);
        boxGeo.move(3.0f, 0, 0);
        PyBallJmeMesh.init();
        flyCam.setEnabled(true);// AHHHhHHHHH
        //flyCam.setDragToRotate(true);
        // Example: Get a mesh with a pyramid pointing in the +X direction and a ball at the center
        Mesh mesh = PyBallJmeMesh.getMesh(true, true, true, true, true, true, true);  // Pyramid pointing toward +X

        // Create the Geometry for the mesh
        Geometry meshGeometry = new Geometry("PyBallMesh", mesh);
        Material py = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md"); 
        py.setBoolean("UseMaterialColors", true); 
        py.setColor("Ambient", ColorRGBA.Green); 
        py.setColor("Diffuse", ColorRGBA.White); 
        // Adds the 'glint' effect
        py.setColor("Specular", ColorRGBA.White); 

        // 1.0 = Matte/Rough, 128.0 = Very Shiny/Polished
        py.setFloat("Shininess", 32f); 
        meshGeometry.setMaterial(py);

        // Attach the meshGeometry to the rootNode to display it in the scene
        rootNode.attachChild(meshGeometry);
        //Vector3f p = new Vector3f(0,0,0);
        //Player.setWorldPosition(p);
        var flyCamToggle = new com.mtsharpgrain.gui.FlyCamToggle(inputManager, flyCam);
        return null;
        
    }
}
