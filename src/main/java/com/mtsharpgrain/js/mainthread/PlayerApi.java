package com.mtsharpgrain.js.mainthread;

import com.jme3.renderer.Camera;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.graalvm.polyglot.HostAccess;

public class PlayerApi {

    private final Camera cam;
    private final Node rootNode;
    private final EngineAccess engine;

    public PlayerApi(Camera cam, Node rootNode, EngineAccess engine) {
        this.cam = cam;
        this.rootNode = rootNode;
        this.engine = engine;
    }

    // cam.getLocation() is render-space (shifts with the floating origin).
    // Scripts should only ever see/set true world coordinates.
    @HostAccess.Export
    public float[] getPosition() {
        return engine.call(() -> {
            Vector3f p = cam.getLocation().subtract(rootNode.getLocalTranslation());
            return new float[]{ p.x, p.y, p.z };
        });
    }

    @HostAccess.Export
    public void setPosition(float x, float y, float z) {
        engine.post(() -> {
            Vector3f trueWorldPos = new Vector3f(x, y, z);
            cam.setLocation(trueWorldPos.add(rootNode.getLocalTranslation()));
        });
    }
}
