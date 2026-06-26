package com.mtsharpgrain.js.mainthread;

import org.graalvm.polyglot.HostAccess;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boilerplate for JS-driven GUI text elements. NOT FUNCTIONAL YET -
 * {@link #guiWord} currently just records the call and returns a placeholder
 * handle; it does not create or render any on-screen text. Wire this up to
 * your real GUI/text system (BitmapText + guiNode, Lemur Label, Nifty, etc.)
 * before relying on it.
 *
 * Intended flow once implemented:
 * <ol>
 *   <li>{@code guiWord()} creates a screen-space text element at (x, y, z)
 *       with the given pixel size, tags it with {@code tag}, and registers
 *       it so it can be manipulated like any other node.</li>
 *   <li>The tag lets JS look the element back up later to update/destroy it,
 *       and is also the tag passed into {@code Engine.onTick(fn, tag)}
 *       callbacks tied to it - so a tick handler can identify which gui
 *       element it's driving.</li>
 * </ol>
 */
public class GuiApi {

    private final NodeRegistry registry;

    // Tracks tag -> handle so JS can look elements up by tag once this is real.
    private final Map<String, Long> handlesByTag = new ConcurrentHashMap<>();

    public GuiApi(NodeRegistry registry) {
        this.registry = registry;
    }

    /**
     * TODO: NOT IMPLEMENTED. Currently does not create any visible GUI element -
     * it only logs the call and stores a placeholder handle under {@code tag}.
     *
     * @param word       text to display
     * @param x          screen-space x position
     * @param y          screen-space y position
     * @param z          depth/ordering (or world z, depending on how you hook this up)
     * @param sizePixels font size in pixels
     * @param tag        identifier used to select/update/destroy this element later,
     *                   and the same tag that will be passed into any
     *                   {@code Engine.onTick(fn, tag)} callback driving it
     * @return a placeholder handle (always {@code -1} until implemented)
     */
    @HostAccess.Export
    public long guiWord(String word, float x, float y, float z, float sizePixels, String tag) {
        // TODO: replace this with actual GUI text creation, roughly:
        //
        //   BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        //   BitmapText text = new BitmapText(font);
        //   text.setSize(sizePixels);
        //   text.setText(word);
        //   text.setLocalTranslation(x, y, z);
        //   guiNode.attachChild(text);
        //   long handle = registry.register(text); // requires NodeRegistry/Spatial-compatible wrapping
        //
        // For now this just logs and returns a fake handle so JS calling this
        // doesn't crash while the real implementation is pending.
        System.out.println("[GuiApi] guiWord() called but not implemented yet: word='" + word
                + "' pos=(" + x + ", " + y + ", " + z + ") size=" + sizePixels + " tag='" + tag + "'");

        long placeholderHandle = -1L;
        handlesByTag.put(tag, placeholderHandle);
        return placeholderHandle;
    }

    /** Looks up the handle previously stored under a tag by guiWord(). Also unused/meaningless until guiWord is real. */
    @HostAccess.Export
    public long getHandleByTag(String tag) {
        return handlesByTag.getOrDefault(tag, -1L);
    }
}
