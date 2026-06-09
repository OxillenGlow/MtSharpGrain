package com.mtsharpgrain.node;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.Camera;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import jme3utilities.sky.CloudLayer;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import jme3utilities.sky.Updater;

/**
 * Utility class to initialize and configure SkyControl for the game.
 * Provides preset configurations for different planetary atmospheres.
 */
public class SkyControlInit {

    /**
     * Initialize SkyControl with default Earth-like settings.
     *
     * @param rootNode the root node to attach SkyControl to
     * @param cam the camera
     * @param assetManager the asset manager
     * @return the initialized SkyControl instance
     */
    public static SkyControl initDefaultSky(Node rootNode, Camera cam, AssetManager assetManager) {
        return initEarthSky(rootNode, cam, assetManager);
    }

    /**
     * Initialize SkyControl with Earth-like settings.
     *
     * @param rootNode the root node to attach SkyControl to
     * @param cam the camera
     * @param assetManager the asset manager
     * @return the initialized SkyControl instance
     */
    public static SkyControl initEarthSky(Node rootNode, Camera cam, AssetManager assetManager) {
        SkyControl skyControl = new SkyControl(
                assetManager,
                cam,
                0.9f,                    // cloud flattening (overhead clouds closer)
                StarsOption.TwoDomes,    // stars on two domes
                true                     // bottom dome for low horizon
        );
        rootNode.addControl(skyControl);

        // Set up lights
        setupLights(rootNode, skyControl);

        // Configure clouds
        CloudLayer layer0 = skyControl.getCloudLayer(0);
        layer0.setColor(new ColorRGBA(1f, 1f, 1f, 0.8f));
        layer0.setTexture("Textures/skies/clouds/dense.png", 0.4f);

        // Light multipliers for Earth
        skyControl.getUpdater().setAmbientMultiplier(1f);
        skyControl.getUpdater().setMainMultiplier(1f);

        skyControl.setCloudiness(0.4f);
        skyControl.setEnabled(true);

        return skyControl;
    }

    /**
     * Initialize SkyControl with Mars-like settings.
     * Features: No moon, smaller sun, orange/rust atmosphere, thin dust clouds.
     *
     * @param rootNode the root node to attach SkyControl to
     * @param cam the camera
     * @param assetManager the asset manager
     * @return the initialized SkyControl instance
     */
    public static SkyControl initMarsSky(Node rootNode, Camera cam, AssetManager assetManager) {
        SkyControl skyControl = new SkyControl(
                assetManager,
                cam,
                0.9f,                    // cloud flattening
                StarsOption.TwoDomes,    // stars on two domes
                true                     // bottom dome
        );
        rootNode.addControl(skyControl);

        // Set up lights
        setupLights(rootNode, skyControl);

        // === MARS SPECIFIC SETTINGS ===
        
        // Smaller sun (Mars is farther from sun, apparent size ~0.35)
        skyControl.setSolarDiameter(0.35f);
        skyControl.setSunStyle("Scaled disc");

        // Hide the moon entirely
        skyControl.setLunarDiameter(0f);

        // === MARTIAN DUST ATMOSPHERE ===
        CloudLayer layer0 = skyControl.getCloudLayer(0);
        // Orange/rust colored clouds
        layer0.setColor(new ColorRGBA(0.9f, 0.6f, 0.3f, 0.8f));
        layer0.setTexture("Textures/skies/clouds/dense.png", 0.2f);

        // Very slow cloud movement (thin atmosphere)
        layer0.setMotion(0.001f, 0f, 0.0005f, 0.00001f);

        // Clear other cloud layers
        for (int i = 1; i < 6; i++) {
            skyControl.getCloudLayer(i).clearTexture();
        }

        // Very sparse clouds on Mars
        skyControl.setCloudiness(0.15f);
        skyControl.setCloudsRate(0.00005f);

        // More atmospheric haze (orange dust)
        skyControl.setTopVerticalAngle(28f);

        // Dimmer lighting (farther from sun)
        skyControl.getUpdater().setAmbientMultiplier(0.65f);
        skyControl.getUpdater().setMainMultiplier(0.8f);

        // Afternoon lighting
        skyControl.getSunAndStars().setHour(14f);

        skyControl.setEnabled(true);

        return skyControl;
    }

    /**
     * Initialize SkyControl with Venus-like settings.
     * Features: No moon, very thick orange atmosphere, high cloud coverage.
     *
     * @param rootNode the root node to attach SkyControl to
     * @param cam the camera
     * @param assetManager the asset manager
     * @return the initialized SkyControl instance
     */
    public static SkyControl initVenusSky(Node rootNode, Camera cam, AssetManager assetManager) {
        SkyControl skyControl = new SkyControl(
                assetManager,
                cam,
                0.9f,
                StarsOption.TwoDomes,
                true
        );
        rootNode.addControl(skyControl);

        setupLights(rootNode, skyControl);

        // Slightly larger sun (Venus is closer to sun)
        skyControl.setSolarDiameter(0.7f);

        // Hide moon
        skyControl.setLunarDiameter(0f);

        // === VENUS THICK ATMOSPHERE ===
        CloudLayer layer0 = skyControl.getCloudLayer(0);
        // Yellowish clouds
        layer0.setColor(new ColorRGBA(1f, 0.9f, 0.4f, 0.9f));
        layer0.setTexture("Textures/skies/clouds/dense.png", 0.5f);

        CloudLayer layer1 = skyControl.getCloudLayer(1);
        // Secondary yellow layer
        layer1.setColor(new ColorRGBA(0.95f, 0.85f, 0.3f, 0.7f));
        layer1.setTexture("Textures/skies/clouds/dense.png", 0.6f);

        // Very thick cloud coverage
        skyControl.setCloudiness(0.9f);
        skyControl.setCloudsRate(0.0001f);

        // Very thick haze
        skyControl.setTopVerticalAngle(45f);

        // Dimmer due to thick clouds blocking sun
        skyControl.getUpdater().setAmbientMultiplier(0.5f);
        skyControl.getUpdater().setMainMultiplier(0.6f);

        skyControl.setEnabled(true);

        return skyControl;
    }

    /**
     * Initialize SkyControl with Moon-like settings.
     * Features: No atmosphere (black sky), stars visible, no sun (or very dim).
     *
     * @param rootNode the root node to attach SkyControl to
     * @param cam the camera
     * @param assetManager the asset manager
     * @return the initialized SkyControl instance
     */
    public static SkyControl initMoonSky(Node rootNode, Camera cam, AssetManager assetManager) {
        SkyControl skyControl = new SkyControl(
                assetManager,
                cam,
                0.9f,
                StarsOption.TwoDomes,
                true
        );
        rootNode.addControl(skyControl);

        setupLights(rootNode, skyControl);

        // Very small sun
        skyControl.setSolarDiameter(0.2f);

        // No moon
        skyControl.setLunarDiameter(0f);

        // === LUNAR VACUUM (NO ATMOSPHERE) ===
        // Clear all cloud layers
        for (int i = 0; i < 6; i++) {
            skyControl.getCloudLayer(i).clearTexture();
        }

        skyControl.setCloudiness(0f);

        // No haze
        skyControl.setTopVerticalAngle(0f);

        // Very dim lighting (sunlight on moon)
        skyControl.getUpdater().setAmbientMultiplier(0.3f);
        skyControl.getUpdater().setMainMultiplier(0.5f);

        skyControl.setEnabled(true);

        return skyControl;
    }

    /**
     * Set up directional lights for SkyControl.
     * Creates and configures the main light and ambient light.
     *
     * @param rootNode the root node
     * @param skyControl the SkyControl instance
     */
    private static void setupLights(Node rootNode, SkyControl skyControl) {
        // Create main directional light (sun)
        DirectionalLight mainLight = new DirectionalLight();
        mainLight.setName("sun");
        mainLight.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());

        // Create ambient light
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setName("ambient");

        // Add lights to scene
        rootNode.addLight(ambientLight);
        rootNode.addLight(mainLight);

        // Configure SkyControl to manage these lights
        Updater updater = skyControl.getUpdater();
        updater.setAmbientLight(ambientLight);
        updater.setMainLight(mainLight);
        updater.addViewPort(null); // Will be set by the application if needed
    }
}
