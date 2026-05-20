package com.mtsharpgrain;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.renderer.Camera;

public class TestInit {
    BlockSelector blockSelector;
    public static void setup(Main app, Player player, WorldAccess worldAccess, MouseListener mouseListener, BlockSelector blockSelector) {
        this.blockSelector = blockSelector;
        blockSelector = new BlockSelector(cam, rootNode, mouseListener);
        mouseListener = new MouseListener();
        worldAccess = new WorldAccess("Data/worlds/my_world");
        player = new Player();
        player.setWorldPosition(new Vector3f(1, 1, 1));

        Camera cam = app.getCamera();
        float aspectRatio = (float) cam.getWidth() / (float) cam.getHeight();
        cam.setFrustumPerspective(45.0f, aspectRatio, 0.5f, 5000.0f);
        app.getFlyByCamera().setEnabled(true);

        // --- 2. Lighting ---
        AmbientLight al = new AmbientLight();
        al.setColor(new ColorRGBA(0.2f, 0.2f, 0.2f, 1.0f));
        app.getRootNode().addLight(al);

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection((new Vector3f(-0.5f, -0.8f, -0.3f)).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        app.getRootNode().addLight(sun);

        // --- 3. Scene Objects (Box & PyBall) ---
        AssetManager assetManager = app.getAssetManager();
        
        Box boxMesh = new Box(1f, 1f, 1f);
        Geometry boxGeo = new Geometry("Colored Box", boxMesh);
        Material boxMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        boxMat.setBoolean("UseMaterialColors", true);
        boxMat.setColor("Ambient", ColorRGBA.Green);
        boxMat.setColor("Diffuse", ColorRGBA.Green);
        boxGeo.setMaterial(boxMat);
        boxGeo.move(3.0f, 0, 0);
        app.getRootNode().attachChild(boxGeo);

        PyBallJmeMesh.init();
        Mesh mesh = PyBallJmeMesh.getMesh(true, true, true, true, true, true, true);
        Geometry meshGeometry = new Geometry("PyBallMesh", mesh);
        Material py = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        py.setBoolean("UseMaterialColors", true);
        py.setColor("Ambient", ColorRGBA.Green);
        py.setColor("Diffuse", ColorRGBA.White);
        py.setColor("Specular", ColorRGBA.White);
        py.setFloat("Shininess", 32f);
        meshGeometry.setMaterial(py);
        app.getRootNode().attachChild(meshGeometry);

        // --- 4. System Initialization ---
        app.blockSelector = new BlockSelector(cam, app.getRootNode(), mouseListener);
        app.getInputManager().addRawInputListener(mouseListener);

        // --- 5. UI / Crosshair ---
        BitmapFont guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        BitmapText ch = new BitmapText(guiFont, false);
        ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        ch.setText("+");
        
        float x = app.getContext().getSettings().getWidth() / 2 - ch.getLineWidth() / 2;
        float y = app.getContext().getSettings().getHeight() / 2 + ch.getLineHeight() / 2;
        ch.setLocalTranslation(x, y, 0);
        app.getGuiNode().attachChild(ch);
    }
}
