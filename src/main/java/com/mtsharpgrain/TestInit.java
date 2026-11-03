
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
        /* A colored lit cube. Needs light source! */ /* A colored lit cube. Needs light source! */ 
        Box boxMesh = new Box(1f,1f,1f); 
        Geometry boxGeo = new Geometry("Colored Box", boxMesh); 
        Material boxMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md"); 
        boxMat.setBoolean("UseMaterialColors", true); 
        boxMat.setColor("Ambient", ColorRGBA.Green); 
        boxMat.setColor("Diffuse", ColorRGBA.Green); 
        boxGeo.setMaterial(boxMat); 
        rootNode.attachChild(boxGeo);
        boxGeo.move(3.0f, 0, 0);
        var boxMesh2 = new Box(1f,1f,1f); 
        var boxGeo2 = new Geometry("Colored Box", boxMesh); 
        Material boxMat2 = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md"); 
        boxMat2.setBoolean("UseMaterialColors", true); 
        boxMat2.setColor("Ambient", ColorRGBA.Blue); 
        boxMat2.setColor("Diffuse", ColorRGBA.Blue); 
        boxGeo2.setMaterial(boxMat2); 
        rootNode.attachChild(boxGeo2);
        boxGeo2.move(0f, 2f, 0);
        var boxMesh3 = new Box(1f,1f,1f); 
        var boxGeo3 = new Geometry("Colored Box", boxMesh); 
        Material boxMat3 = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md"); 
        boxMat3.setBoolean("UseMaterialColors", true); 
        boxMat3.setColor("Ambient", ColorRGBA.Red); 
        boxMat3.setColor("Diffuse", ColorRGBA.Red); 
        boxGeo3.setMaterial(boxMat3); 
        rootNode.attachChild(boxGeo3);
        boxGeo3.move(-2.4f, -1f, 0);
        PyBallJmeMesh.init();
        flyCam.setEnabled(true);// AHHHhHHHHH
        var flyCamToggle = new com.mtsharpgrain.gui.FlyCamToggle(inputManager, flyCam);
        return null;
        
    }
}
