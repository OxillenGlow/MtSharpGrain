package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.jme3.light.PointLight;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.shadow.PointLightShadowRenderer;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Owns point-light shadow renderers for the main scene viewport.
 *
 * Point-light shadows require six shadow-map views per light. They are
 * therefore opt-in rather than enabled for every mod-created point light.
 * All methods must be called on the render thread.
 */
public final class PointLightShadowManager {
    private static final int SHADOW_MAP_SIZE = 1024;
    private static final float SHADOW_INTENSITY = 0.7f;

    private final AssetManager assets;
    private final ViewPort viewPort;
    private final Node rootNode;
    private final Map<PointLight, PointLightShadowRenderer> renderers = new IdentityHashMap<>();

    public PointLightShadowManager(AssetManager assets, ViewPort viewPort, Node rootNode) {
        this.assets = assets;
        this.viewPort = viewPort;
        this.rootNode = rootNode;
    }

    public void setEnabled(PointLight light, boolean enabled) {
        if (enabled) {
            if (renderers.containsKey(light)) return;

            PointLightShadowRenderer renderer =
                    new PointLightShadowRenderer(assets, SHADOW_MAP_SIZE);
            renderer.setLight(light);
            renderer.setShadowIntensity(SHADOW_INTENSITY);
            viewPort.addProcessor(renderer);
            renderers.put(light, renderer);
        } else {
            PointLightShadowRenderer renderer = renderers.remove(light);
            if (renderer != null) {
                viewPort.removeProcessor(renderer);
            }
        }
    }

    /**
     * Detaches every registered renderer and its light. Called when a world
     * is unloaded so shadow processors cannot retain references to old worlds.
     */
    public void clear() {
        for (Map.Entry<PointLight, PointLightShadowRenderer> entry : renderers.entrySet()) {
            viewPort.removeProcessor(entry.getValue());
            rootNode.removeLight(entry.getKey());
        }
        renderers.clear();
    }
}
