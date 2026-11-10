package com.mtsharpgrain.gui;

import com.jme.igui.IGui;
import com.jme.igui.IGuiAppState;
import com.jme.igui.IGuiComponent;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;

import java.util.function.BiFunction;

/**
 * Square-relative IGui wrapper.
 * Virtual space is a square of side = window height, left-aligned.
 * Default methods use that square. *Screen methods keep old full-window relative coords.
 */
public final class SquareIGui implements IGui {

    private final AssetManager assetManager;
    private final AppStateManager stateManager;
    private final InputManager inputManager;
    private final Node root;

    private IGui delegate;
    private int realW;
    private int realH;

    private SquareIGui(AssetManager am, AppStateManager sm, InputManager im, Node root) {
        this.assetManager = am;
        this.stateManager = sm;
        this.inputManager = im;
        this.root = root;
    }

    public static SquareIGui create(AssetManager am, AppStateManager sm, InputManager im,
                                    Node root, int screenW, int screenH) {
        var gui = new SquareIGui(am, sm, im, root);
        gui.reshape(screenW, screenH);
        return gui;
    }

    /**
     * Rebuild the inner gui against the current window.
     * Same wrapper instance stays valid. Caller must re-add persistent widgets.
     */
    public void reshape(int width, int height) {
        if (height <= 0) height = 1;
        if (width <= 0) width = 1;
        realW = width;
        realH = height;
        if (delegate != null) {
            delegate.destroy();
            delegate = null;
        }
        delegate = IGuiAppState.newRelative(assetManager, stateManager, inputManager, root, height, height);
    }

    public int getRealWidth() { return realW; }
    public int getRealHeight() { return realH; }
    public float getAspect() { return realH == 0 ? 1f : (float) realW / realH; }

    /** Full-window X (0..1 of width) into square-space X. */
    public float toSquareX(float screenX) {
        return screenX * getAspect();
    }

    public IGuiComponent textScreen(String text, float posX, float posY) {
        return text(text, toSquareX(posX), posY);
    }

    public IGuiComponent textScreen(String text, float posX, float posY,
                                    BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return text(text, toSquareX(posX), posY, onClick);
    }

    public IGuiComponent textScreen(String text, float posX, float posY, boolean persistent) {
        return text(text, toSquareX(posX), posY, persistent);
    }

    public IGuiComponent textScreen(String text, float posX, float posY, boolean persistent,
                                    BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return text(text, toSquareX(posX), posY, persistent, onClick);
    }

    public IGuiComponent imageScreen(String img, float posX, float posY) {
        return image(img, toSquareX(posX), posY);
    }

    public IGuiComponent imageScreen(String img, float posX, float posY,
                                     BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return image(img, toSquareX(posX), posY, onClick);
    }

    public IGuiComponent imageScreen(String img, float posX, float posY, boolean persistent) {
        return image(img, toSquareX(posX), posY, persistent);
    }

    public IGuiComponent imageScreen(String img, float posX, float posY, boolean persistent,
                                     BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return image(img, toSquareX(posX), posY, persistent, onClick);
    }

    public SquareIGui imageSizeScreen(float w, float h) {
        imageSize(w * getAspect(), h);
        return this;
    }

    public SquareIGui imageSizeScreen(Vector2f size) {
        return imageSizeScreen(size.x, size.y);
    }

    private IGui d() {
        if (delegate == null) throw new IllegalStateException("SquareIGui not initialised – call reshape first");
        return delegate;
    }

    @Override public IGui textColor(ColorRGBA color) { d().textColor(color); return this; }
    @Override public IGui textSize(float size) { d().textSize(size); return this; }
    @Override public IGui textFont(String path) { d().textFont(path); return this; }
    @Override public IGui textFontStyle(String style) { d().textFontStyle(style); return this; }
    @Override public IGui textRighToLeft(boolean v) { d().textRighToLeft(v); return this; }
    @Override public IGui textHAlign(String align) { d().textHAlign(align); return this; }
    @Override public IGui textVAlign(String align) { d().textVAlign(align); return this; }
    @Override public IGui imageFlip(boolean v) { d().imageFlip(v); return this; }
    @Override public IGui imageAlpha(boolean v) { d().imageAlpha(v); return this; }
    @Override public IGui imageHAlign(String align) { d().imageHAlign(align); return this; }
    @Override public IGui imageVAlign(String align) { d().imageVAlign(align); return this; }
    @Override public IGui imageSize(String image) { d().imageSize(image); return this; }
    @Override public IGui imageSize(float w, float h) { d().imageSize(w, h); return this; }
    @Override public IGui imageSize(Vector2f size) { d().imageSize(size); return this; }
    @Override public IGui imageScale(float s) { d().imageScale(s); return this; }
    @Override public IGui imageScale(float x, float y) { d().imageScale(x, y); return this; }
    @Override public IGui imageScale(Vector2f s) { d().imageScale(s); return this; }
    @Override public IGui imageColor(ColorRGBA color) { d().imageColor(color); return this; }
    @Override public IGui zIndex(float v) { d().zIndex(v); return this; }
    @Override public IGui push() { d().push(); return this; }
    @Override public IGui push(boolean inherit) { d().push(inherit); return this; }
    @Override public IGui pop() { d().pop(); return this; }

    @Override public IGuiComponent text(String text, float posX, float posY) {
        return d().text(text, posX, posY);
    }
    @Override public IGuiComponent text(String text, float posX, float posY,
                                        BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return d().text(text, posX, posY, onClick);
    }
    @Override public IGuiComponent text(String text, float posX, float posY, boolean persistent) {
        return d().text(text, posX, posY, persistent);
    }
    @Override public IGuiComponent text(String text, float posX, float posY, boolean persistent,
                                        BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return d().text(text, posX, posY, persistent, onClick);
    }

    @Override public IGuiComponent image(String img, float posX, float posY) {
        return d().image(img, posX, posY);
    }
    @Override public IGuiComponent image(String img, float posX, float posY,
                                         BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return d().image(img, posX, posY, onClick);
    }
    @Override public IGuiComponent image(String img, float posX, float posY, boolean persistent) {
        return d().image(img, posX, posY, persistent);
    }
    @Override public IGuiComponent image(String img, float posX, float posY, boolean persistent,
                                         BiFunction<IGuiMouseEvent, Object, Boolean> onClick) {
        return d().image(img, posX, posY, persistent, onClick);
    }

    @Override public float getTextLineWidth(String text) { return d().getTextLineWidth(text); }
    @Override public float getTextLineHeight(String text) { return d().getTextLineHeight(text); }
    @Override public ColorRGBA getTextColor() { return d().getTextColor(); }
    @Override public float getTextSize() { return d().getTextSize(); }
    @Override public Object getTextFont() { return d().getTextFont(); }
    @Override public boolean isTextRightToLeft() { return d().isTextRightToLeft(); }
    @Override public String getTextHAlign() { return d().getTextHAlign(); }
    @Override public String getTextVAlign() { return d().getTextVAlign(); }
    @Override public String getImageHAlign() { return d().getImageHAlign(); }
    @Override public String getImageVAlign() { return d().getImageVAlign(); }
    @Override public boolean getImageFlip() { return d().getImageFlip(); }
    @Override public boolean getImageAlpha() { return d().getImageAlpha(); }
    @Override public Vector2f getImageSize() { return d().getImageSize(); }
    @Override public Vector2f getImageScale() { return d().getImageScale(); }
    @Override public ColorRGBA getImageColor() { return d().getImageColor(); }
    @Override public float getZIndex() { return d().getZIndex(); }

    @Override public void clear(boolean destroyAllPersistent) { d().clear(destroyAllPersistent); }
    @Override public void destroy() {
        if (delegate != null) {
            delegate.destroy();
            delegate = null;
        }
    }
}
