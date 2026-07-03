package com.mtsharpgrain.js.mainthread;

import com.jme3.renderer.Camera;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.HostAccess;

public class PlayerApi {

    private final Camera cam;

    public PlayerApi(Camera cam) {
        this.cam = cam;
    }

    @HostAccess.Export
    public float[] getPosition() {
        Vector3f p = cam.getLocation();
        return new float[]{ p.x, p.y, p.z };
    }

    @HostAccess.Export
    public void setPosition(float x, float y, float z) {
        cam.setLocation(new Vector3f(x, y, z));
    }
}
