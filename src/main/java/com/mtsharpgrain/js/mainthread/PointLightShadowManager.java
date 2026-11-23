package com.mtsharpgrain.js.mainthread;

import com.jme3.asset.AssetManager;
import com.jme3.light.PointLight;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.shadow.PointLightShadowFilter;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Owns opt-in point-light shadow filters for the main scene viewport.
 *
 * Each enabled point light still renders six shadow-map faces, but the
 * shadow is applied in screen space (PointLightShadowFilter) instead of a
 * second geometry pass. That stays cheaper once chunk meshes fill the view.
 * ShadowMode.Receive is ignored by filters: anything that writes depth
 * receives the shadow.
 *
 * The FilterPostProcessor is attached only while at least one light has
 * shadows enabled, and removed again when the last one is turned off.
 * All methods must be called on the render thread.
 */
public final class PointLightShadowManager {
    private static final int SHADOW_MAP_SIZE = 512;
    private static final float SHADOW_INTENSITY = 0.7f;
    /** Matches AppSettings.setSamples in Main. */
    private static final int SAMPLES = 4;

    private final AssetManager assets;
    private final ViewPort viewPort;
    private final Node rootNode;
    private final Map<PointLight, PointLightShadowFilter> filters = new IdentityHashMap<>();
    private FilterPostProcessor post;

    public PointLightShadowManager(AssetManager assets, ViewPort viewPort, Node rootNode) {
        this.assets = assets;
        this.viewPort = viewPort;
        this.rootNode = rootNode;
    }

    public void setEnabled(PointLight light, boolean enabled) {
        if (enabled) {
            if (filters.containsKey(light)) return;

            ensurePost();
            var filter = new PointLightShadowFilter(assets, SHADOW_MAP_SIZE);
            filter.setLight(light);
            filter.setShadowIntensity(SHADOW_INTENSITY);
            filter.setEnabled(true);
            post.addFilter(filter);
            filters.put(light, filter);
        } else {
            var filter = filters.remove(light);
            if (filter != null && post != null) {
                post.removeFilter(filter);
            }
            if (filters.isEmpty()) detachPost();
        }
    }

    /**
     * Drops every registered filter and its light. Called when a world is
     * unloaded so post processors cannot retain references to old worlds.
     */
    public void clear() {
        if (post != null) {
            for (var filter : filters.values()) {
                post.removeFilter(filter);
            }
        }
        for (var light : filters.keySet()) {
            rootNode.removeLight(light);
        }
        filters.clear();
        detachPost();
    }

    private void ensurePost() {
        if (post != null) return;
        post = new FilterPostProcessor(assets);
        post.setNumSamples(SAMPLES);
        viewPort.addProcessor(post);
    }

    private void detachPost() {
        if (post == null) return;
        viewPort.removeProcessor(post);
        post = null;
    }
}
