package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.scene.control.LightControl;
import java.util.HashMap;
import java.util.Map;
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
    private final Node rootNode;

    // handle -> Light, for handles that are light-carrier nodes created by
    // createLight(). Not every registry handle has an entry here — only
    // ones that came from createLight(). Checked in destroy() so removing
    // the node also removes the light from rootNode's light list, instead
    // of leaving it registered (and shining) with no owning Spatial.
    private final Map<Long, Light> lightsByHandle = new HashMap<>();

    public SceneApi(NodeRegistry registry, AssetManager assets, WorldAccessor world, Node rootNode) {
        this.rootNode = rootNode;
        this.registry = registry;
        this.assets = assets;
        this.world = world;
    }

    @HostAccess.Export
    public long createNode(String name) {
        return registry.register(new Node(name));
    }

    /**
     * Creates a shaded cube (Lighting.j3md, same lighting model as terrain
     * blocks in ChunkMeshBuilder) instead of the previous Unshaded material.
     *
     * NOTE (flagging, not deciding for you): shading requires a light in the
     * scene - TestInit.init() already adds an AmbientLight + DirectionalLight
     * to rootNode, so this works as-is for cubes attached anywhere under
     * rootNode (light lists are inherited down the scene graph). If a script
     * ever builds a scene graph that isn't under rootNode, it won't be lit.
     * Shadows: ShadowMode defaults to Inherit, and Main.java sets
     * CastAndReceive on rootNode, so cubes attached under handle 0 (rootNode)
     * will cast/receive shadows automatically; cubes attached elsewhere won't
     * unless that subtree's root also gets a ShadowMode set.
     */
    @HostAccess.Export
    public long createCube(String name, float size) {
        Box box = new Box(size / 2f, size / 2f, size / 2f);
        Geometry geom = new Geometry(name, box);

        Material mat = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient", new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
        mat.setColor("Diffuse", ColorRGBA.White);
        mat.setColor("Specular", ColorRGBA.White);
        mat.setFloat("Shininess", 16f);
        geom.setMaterial(mat);

        return registry.register(geom);
    }

    @HostAccess.Export
    public long createRectangle(String name, float x, float y, float z) {
        Box box = new Box(x / 2f, y / 2f, z / 2f);
        Geometry geom = new Geometry(name, box);

        Material mat = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient", new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
        mat.setColor("Diffuse", ColorRGBA.White);
        mat.setColor("Specular", ColorRGBA.White);
        mat.setFloat("Shininess", 16f);
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
     * 
     * @param handle 
     * @return float[3] 0 is x; 1 is y 2; is z
     */
    @HostAccess.Export
    public float[] getPosition(long handle) {
        Vector3f worldPos = registry.get(handle).getWorldTranslation();
        Vector3f trueWorldPos = worldPos.subtract(rootNode.getLocalTranslation());
        return new float[] { trueWorldPos.x, trueWorldPos.y, trueWorldPos.z };
    }

    /**
     * Sets local rotation from Euler angles, in RADIANS, order (pitch=x, yaw=y, roll=z).
     * In JS: {@code Scene.setRotation(handle, 0, Math.PI / 2, 0);}
     */
    @HostAccess.Export
    public void setRotation(long handle, float xRad, float yRad, float zRad) {
        Quaternion q = new Quaternion().fromAngles(xRad, yRad, zRad);
        registry.get(handle).setLocalRotation(q);
    }

    /**
     * Returns the LOCAL rotation of the given handle as Euler angles in
     * radians (pitch, yaw, roll) - mirrors setRotation's parameter order.
     * In JS: {@code const r = Scene.getRotation(handle); r[0], r[1], r[2]}
     */
    @HostAccess.Export
    public float[] getRotation(long handle) {
        float[] angles = new float[3];
        registry.get(handle).getLocalRotation().toAngles(angles);
        return angles;
    }

    @HostAccess.Export
    public void setColor(long handle, float r, float g, float b, float a) {
        Spatial spatial = registry.get(handle);
        if (spatial instanceof Geometry) {
            ((Geometry) spatial).getMaterial().setColor("Diffuse", new ColorRGBA(r, g, b, a));
        }
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

    /**
     * Creates a PointLight and an invisible carrier Node wired together via
     * LightControl (see jME wiki: "Light Follows Spatial") — the control
     * copies the node's world translation into the light's position every
     * frame, so the returned handle behaves like any other Scene node for
     * positioning purposes: setPosition/getPosition/attachChild all work on
     * it unmodified.
     *
     * NOTE (flagging, not deciding): the light is added to rootNode's light
     * list, so — same as createCube — it lights everything under rootNode
     * regardless of where the carrier node itself gets attached. If a script
     * ever needs a light scoped to a subtree instead of the whole scene,
     * this would need an overload that takes a parent handle for both the
     * addLight() call and the attach.
     *
     * @param radius world-space falloff distance
     */
    @HostAccess.Export
    public long createLight(String name, float r, float g, float b, float radius) {
        PointLight light = new PointLight();
        light.setColor(new ColorRGBA(r, g, b, 1f));
        light.setRadius(radius);
        rootNode.addLight(light);

        Node carrier = new Node(name);
        carrier.addControl(new LightControl(light));

        long handle = registry.register(carrier);
        lightsByHandle.put(handle, light);
        return handle;
    }

    @HostAccess.Export
    public void setLightColor(long handle, float r, float g, float b) {
        Light light = lightsByHandle.get(handle);
        if (light == null) {
            throw new IllegalArgumentException("Handle " + handle + " is not a light");
        }
        ((PointLight) light).setColor(new ColorRGBA(r, g, b, 1f));
    }

    @HostAccess.Export
    public void setLightRadius(long handle, float radius) {
        Light light = lightsByHandle.get(handle);
        if (light == null) {
            throw new IllegalArgumentException("Handle " + handle + " is not a light");
        }
        ((PointLight) light).setRadius(radius);
    }

    @HostAccess.Export
    public void destroy(long handle) {
        Spatial spatial = registry.get(handle);
        spatial.removeFromParent();
        registry.free(handle);

        // If this handle was a light carrier, the light itself has to be
        // pulled out of rootNode's light list explicitly — removeFromParent()
        // above only detaches the invisible carrier node, it does not touch
        // the light, which was added via rootNode.addLight(), not attachChild().
        Light light = lightsByHandle.remove(handle);
        if (light != null) {
            rootNode.removeLight(light);
        }
    }
}
