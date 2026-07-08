package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import org.graalvm.polyglot.HostAccess;

/**
 * The scene-manipulation surface exposed to JS as the {@code Scene} global.
 * Every method here is annotated with {@link HostAccess.Export} - paired with
 * a {@code Context} built using {@code HostAccess.EXPLICIT}, this means JS
 * can ONLY call what's listed here. No reflection, no arbitrary jME access.
 *
 * All methods must be called from the render/main thread.
 */
public class SceneApi {

    private final NodeRegistry registry;
    private final AssetManager assets;
    private final WorldAccessor world;

    public SceneApi(NodeRegistry registry, AssetManager assets, WorldAccessor world) {
        this.registry = registry;
        this.assets = assets;
        this.world = world;
    }

    @HostAccess.Export
    public long createNode(String name) {
        return registry.register(new Node(name));
    }

    @HostAccess.Export
    public long createCube(String name, float size) {
        Box box = new Box(size / 2f, size / 2f, size / 2f);
        Geometry geom = new Geometry(name, box);
        Material mat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.White);
        geom.setMaterial(mat);
        return registry.register(geom);
    }

    @HostAccess.Export
    public void attachChild(long parentHandle, long childHandle) {
        Spatial parent = registry.get(parentHandle);
        if (!(parent instanceof Node)) {
            throw new IllegalArgumentException("Handle " + parentHandle + " is not a Node - cannot attach children to it");
        }
        ((Node) parent).attachChild(registry.get(childHandle));
    }

    @HostAccess.Export
    public void setPosition(long handle, float x, float y, float z) {
        registry.get(handle).setLocalTranslation(x, y, z);
    }

    /**
     * Returns the WORLD position (not local translation) of the given handle.
     * In JS: {@code const p = Scene.getPosition(handle); p[0], p[1], p[2]}
     * - GraalJS exposes Java float[] as an indexable, length-bearing array-like value.
     */
    @HostAccess.Export
    public float[] getPosition(long handle) {
        Vector3f worldPos = registry.get(handle).getWorldTranslation();
        Vector3f trueWorldPos = worldPos.subtract(rootNode.getLocalTranslation());
        return new float[] { trueWorldPos.x, trueWorldPos.y, trueWorldPos.z };
    }

    @HostAccess.Export
    public void setColor(long handle, float r, float g, float b, float a) {
        Spatial spatial = registry.get(handle);
        if (spatial instanceof Geometry) {
            ((Geometry) spatial).getMaterial().setColor("Color", new ColorRGBA(r, g, b, a));
        }
    }

    @HostAccess.Export
    public void destroy(long handle) {
        Spatial spatial = registry.get(handle);
        spatial.removeFromParent();
        registry.free(handle);
    }

    /**
     * Returns the block id at the given world block coordinates.
     * Forwards to {@link WorldAccessor} - wire a real implementation in there.
     * NOTE: assumes integer block coordinates, not world-space floats - adjust
     * if your world uses a different coordinate convention.
     */
    @HostAccess.Export
    public int getBlockId(int x, int y, int z) {
        return world.getBlockId(x, y, z);
    }
}
