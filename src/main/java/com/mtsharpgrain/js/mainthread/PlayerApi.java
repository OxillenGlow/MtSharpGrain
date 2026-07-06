package com.mtsharpgrain.js.mainthread;

import com.jme3.renderer.Camera;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.graalvm.polyglot.HostAccess;

public class PlayerApi {

    private final Camera cam;
    private final Node rootNode;

    public PlayerApi(Camera cam, Node rootNode) {
        this.cam = cam;
        this.rootNode = rootNode;
    }

    // cam.getLocation() is render-space (shifts with the floating origin).
    // Scripts should only ever see/set true world coordinates.
    @HostAccess.Export
    public float[] getPosition() {
        Vector3f p = cam.getLocation().subtract(rootNode.getLocalTranslation());
        return new float[]{ p.x, p.y, p.z };
    }

    @HostAccess.Export
    public void setPosition(float x, float y, float z) {
        Vector3f trueWorldPos = new Vector3f(x, y, z);
        cam.setLocation(trueWorldPos.add(rootNode.getLocalTranslation()));
    }
}
