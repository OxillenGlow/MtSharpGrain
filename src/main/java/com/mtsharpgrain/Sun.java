package com.mtsharpgrain;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

/**
 * Orbiting day/night sun: a small unshaded cube + rotating DirectionalLight
 */
public class Sun {

    private float rotationPeriodSeconds;

    // ── Color model — same three anchor colors as sun.js ────────────────
    private static final ColorRGBA DAWN_COLOR  = new ColorRGBA(0.35f, 0.05f, 0f,   1f);
    private static final ColorRGBA NOON_COLOR  = new ColorRGBA(1.0f,  0.55f, 0.15f, 1f);
    private static final ColorRGBA NIGHT_COLOR = new ColorRGBA(0f,    0f,    0f,   1f);

    private final Geometry cube;
    private final DirectionalLight light;

    private float angleDeg = 0f; // 0 = dawn, 90 = noon, 180 = dusk, 180-360 = night

    /** Convenience constructor — same defaults as sun.js (170 orbit, ~1.04 cube, 400 falloff, 12 min period). */
    public Sun(AssetManager assetManager, Node rootNode) {
        this(assetManager, rootNode, 1.04f, 720f);
    }

    public Sun(AssetManager assetManager, Node rootNode, float size, float rotationPeriodSeconds) {
        this.rotationPeriodSeconds = rotationPeriodSeconds;
        
        //─── Visible cube ────────────────────────────────────────────────
        Box box = new Box(size, size, size);
        cube = new Geometry("SunCube", box);

        // Make the cube unshaded and visually bright so it looks like a sun
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.White); // updated every frame
        cube.setMaterial(mat);
        rootNode.attachChild(cube);
        cube.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);// so it doesent cast a strange shaddow

        // Use a directional light to act as the sun (covers the whole scene).
        // DirectionalLight is attached to the scene (rootNode) so it affects all spatials.
        light = new DirectionalLight();
        // initial direction: from upper-left-ish; will be updated each frame in update()
        light.setDirection(new Vector3f(-0.3f, -1f, -0.1f).normalizeLocal());
        light.setColor(NOON_COLOR);
        rootNode.addLight(light);
    }

    /**
     * Advances the orbit and repositions/recolors the sun.
     *
     * @param tpf                time per frame
     * @param playerTrueWorldPos player's TRUE world position (e.g. what
     *                           PlayerApi.getPosition()/cam - rootNode.translation
     *                           gives you — NOT raw cam.getLocation()).
     *                           Because cube/lightCarrier are direct children
     *                           of rootNode, setting their local translation
     *                           to a "true world" coordinate lands them in
     *                           the correct render-space spot automatically
     *                           — same math SceneApi.setPosition relies on.
     */
    public void update(float tpf, Vector3f playerTrueWorldPos) {
        float degPerSecond = 360f / rotationPeriodSeconds;
        angleDeg = (angleDeg + degPerSecond * tpf) % 360f;
        float rad = angleDeg * FastMath.DEG_TO_RAD;
        var orbitRadius = Main.VIEW_DISTANCE*16;
        float x = playerTrueWorldPos.x + FastMath.cos(rad) * orbitRadius;
        float y = playerTrueWorldPos.y + FastMath.sin(rad) * orbitRadius;
        float z = playerTrueWorldPos.z;

        Vector3f pos = new Vector3f(x, y, z);
        cube.setLocalTranslation(pos);
        // For a directional sun: set the light direction based on orbit angle.
        // The direction vector is the direction FROM which the light shines.
        // We point it roughly from the sun toward the scene (player).
        Vector3f dir = new Vector3f(-FastMath.cos(rad), -FastMath.sin(rad), 0f).normalizeLocal();
        light.setDirection(dir);

        ColorRGBA c = colorForAngle(angleDeg);
        // Make cube brighter and unshaded (so it's always visible): amplify color a bit.
        ColorRGBA bright = new ColorRGBA(
            Math.min(1f, c.r * 1.5f),
            Math.min(1f, c.g * 1.5f),
            Math.min(1f, c.b * 1.5f),
            1f
        );
        cube.getMaterial().setColor("Color", bright);
        light.setColor(c);
    }

    private static ColorRGBA colorForAngle(float deg) {
        if (deg > 180f && deg < 360f) return NIGHT_COLOR;

        // deg in [0, 180] — triangle wave peaking at 90 (noon)
        float t = 1f - Math.abs(deg - 90f) / 90f; // 0 at dawn/dusk, 1 at noon
        return new ColorRGBA(
            DAWN_COLOR.r + (NOON_COLOR.r - DAWN_COLOR.r) * t,
            DAWN_COLOR.g + (NOON_COLOR.g - DAWN_COLOR.g) * t,
            DAWN_COLOR.b + (NOON_COLOR.b - DAWN_COLOR.b) * t,
            1f
        );
    }

    public void setRotationPeriodSeconds(float seconds) {
        this.rotationPeriodSeconds = seconds;
    }

    public float getRotationPeriodSeconds() {
        return rotationPeriodSeconds;
    }

    public float getAngleDeg() {
        return angleDeg;
    }

    /**
     * Allow setting the current angle (for loading saved time).
     */
    public void setAngleDeg(float deg) {
        // normalize to [0,360)
        deg = deg % 360f;
        if (deg < 0f) deg += 360f;
        this.angleDeg = deg;
    }

    /**
     * Expose the underlying DirectionalLight so other systems (shadows, etc.) can use it.
     * @return The jme directional light
     */
    public DirectionalLight getLight() {
        return light;
    }

    /**
     * Convenience: attach this sun's light to a DirectionalLightShadowRenderer so
     * the renderer uses the same light instance for shadow casting.
     * @param dlsr jmonkey engine shader
     */
    public void attachShadowRenderer(com.jme3.shadow.DirectionalLightShadowRenderer dlsr) {
        if (dlsr != null) dlsr.setLight(light);
    }
}
