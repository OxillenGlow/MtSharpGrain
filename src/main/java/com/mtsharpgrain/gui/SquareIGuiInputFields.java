package com.mtsharpgrain.gui;

import com.jme.igui.IGuiMouseEvent;
import com.jme.igui.extras.IGuiInputFields;
import com.jme.igui.extras.IGuiInputFieldsAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.InputManager;
import com.jme3.input.event.KeyInputEvent;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Input-fields plugin wrapper. Default input() is square-relative.
 * inputScreen() keeps old full-window relative coords.
 */
public final class SquareIGuiInputFields implements IGuiInputFields {

    private final SquareIGui squareGui;
    private final IGuiInputFields delegate;

    private SquareIGuiInputFields(SquareIGui squareGui, IGuiInputFields delegate) {
        this.squareGui = squareGui;
        this.delegate = delegate;
    }

    public static SquareIGuiInputFields create(SquareIGui gui, InputManager im, AppStateManager sm) {
        var plugin = IGuiInputFieldsAppState.newPlugin(gui, im, sm);
        return new SquareIGuiInputFields(gui, plugin);
    }

    @Override
    public IGuiInputFields input(String content, float posX, float posY,
                                 BiFunction<IGuiMouseEvent, Object, Boolean> onMouse,
                                 Consumer<KeyInputEvent> onKey, boolean persistent) {
        delegate.input(content, posX, posY, onMouse, onKey, persistent);
        return this;
    }

    @Override
    public IGuiInputFields input(String content, float posX, float posY,
                                 BiFunction<IGuiMouseEvent, Object, Boolean> onMouse,
                                 Consumer<KeyInputEvent> onKey) {
        return input(content, posX, posY, onMouse, onKey, false);
    }

    @Override
    public IGuiInputFields input(String content, float posX, float posY,
                                 Consumer<KeyInputEvent> onKey, boolean persistent) {
        return input(content, posX, posY, null, onKey, persistent);
    }

    @Override
    public IGuiInputFields input(String content, float posX, float posY,
                                 Consumer<KeyInputEvent> onKey) {
        return input(content, posX, posY, null, onKey, false);
    }

    public IGuiInputFields inputScreen(String content, float posX, float posY,
                                       BiFunction<IGuiMouseEvent, Object, Boolean> onMouse,
                                       Consumer<KeyInputEvent> onKey, boolean persistent) {
        return input(content, squareGui.toSquareX(posX), posY, onMouse, onKey, persistent);
    }

    public IGuiInputFields inputScreen(String content, float posX, float posY,
                                       Consumer<KeyInputEvent> onKey, boolean persistent) {
        return inputScreen(content, posX, posY, null, onKey, persistent);
    }

    public IGuiInputFields inputScreen(String content, float posX, float posY,
                                       Consumer<KeyInputEvent> onKey) {
        return inputScreen(content, posX, posY, null, onKey, false);
    }

    @Override public void clear(boolean destroyAllPersistent) { delegate.clear(destroyAllPersistent); }
    @Override public void destroy() { delegate.destroy(); }
}
