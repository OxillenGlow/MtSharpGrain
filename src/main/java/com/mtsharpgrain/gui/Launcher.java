package com.mtsharpgrain.gui;

import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.math.ColorRGBA;
import com.mtsharpgrain.Main;
import com.mtsharpgrain.storage.WorldGit;

public final class Launcher {
    private Launcher() {}

    private static final int PAGE = 7;
    private static int page;
    private static volatile String status = "";
    private static volatile boolean busy;
    private static String nameDraft = "";
    private static String urlDraft = "";
    private static String focus = "name"; // "name" or "url"

    public static void draw(IGui gui) {
        var path = GameState.guiState;
        dim(gui);
        card(gui);

        gui.textFont("Interface/Fonts/Console.fnt");
        gui.textHAlign("center");
        gui.textVAlign("center");
        gui.textColor(ColorRGBA.White);
        gui.textSize(0.04f);
        gui.text("Mt. SharpGrain", 0.5f, 0.86f, false);

        gui.textSize(0.016f);
        gui.textColor(ColorRGBA.fromRGBA255(180, 190, 200, 255));
        gui.text(status == null ? "" : status, 0.5f, 0.18f, false);

        if ("launcher/new".equals(path)) drawNew(gui);
        else if ("launcher/clone".equals(path)) drawClone(gui);
        else drawList(gui);
    }

    private static void drawList(IGui gui) {
        var worlds = WorldGit.listWorlds();
        int pages = Math.max(1, (worlds.size() + PAGE - 1) / PAGE);
        if (page >= pages) page = pages - 1;
        int from = page * PAGE;
        int to = Math.min(worlds.size(), from + PAGE);

        gui.textSize(0.018f);
        gui.textHAlign("left");
        gui.textVAlign("center");
        if (worlds.isEmpty()) {
            gui.textColor(ColorRGBA.Gray);
            gui.text("No worlds yet.", 0.22f, 0.70f, false);
        }
        float y = 0.74f;
        for (int i = from; i < to; i++) {
            var name = worlds.get(i);
            var dir = WorldGit.worldsRoot().resolve(name);
            var git = WorldGit.isGit(dir);
            float row = y;

            gui.textColor(ColorRGBA.White);
            gui.textSize(0.02f);
            gui.text(name + (git ? "  (git)" : ""), 0.20f, row, false);

            glass(gui, 0.62f, row, 0.16f, 0.055f, ColorRGBA.Green, "Enter", () -> {
                if (!busy) Main.INSTANCE.enterWorld(name, "home");
            });
            glass(gui, 0.80f, row, 0.14f, 0.055f, new ColorRGBA(0.4f, 0.7f, 1f, 1f), "Mods", () -> {
                if (!busy) Main.INSTANCE.enterWorld(name, "home/modview");
            });
            if (git) {
                glass(gui, 0.94f, row, 0.10f, 0.055f, ColorRGBA.Yellow, "Up", () -> pullAsync(name));
            }
            y -= 0.07f;
        }

        gui.textHAlign("center");
        gui.textSize(0.016f);
        gui.textColor(ColorRGBA.White);
        gui.text("<", 0.42f, 0.24f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT && page > 0) page--;
            return true;
        });
        gui.text((page + 1) + " / " + pages, 0.50f, 0.24f, false);
        gui.text(">", 0.58f, 0.24f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT && page + 1 < pages) page++;
            return true;
        });

        glass(gui, 0.36f, 0.10f, 0.24f, 0.08f, ColorRGBA.Green, "New world", () -> {
            if (busy) return;
            nameDraft = "";
            status = "";
            GameState.navigateTo("launcher/new");
        });
        glass(gui, 0.64f, 0.10f, 0.28f, 0.08f, new ColorRGBA(0.3f, 0.85f, 0.95f, 1f), "From GitHub", () -> {
            if (busy) return;
            nameDraft = "";
            urlDraft = "https://github.com/";
            focus = "url";
            status = "";
            GameState.navigateTo("launcher/clone");
        });
    }

    private static void drawNew(IGui gui) {
        gui.textHAlign("center");
        gui.textSize(0.022f);
        gui.textColor(ColorRGBA.White);
        gui.text("New world name", 0.5f, 0.62f, false);
        field(gui, nameDraft.isEmpty() ? "my_world" : nameDraft, 0.5f, 0.52f);
        captureKeys(gui, true);

        glass(gui, 0.38f, 0.36f, 0.22f, 0.08f, ColorRGBA.Gray, "Back", () -> {
            if (!busy) GameState.navigateTo("launcher");
        });
        glass(gui, 0.62f, 0.36f, 0.22f, 0.08f, ColorRGBA.Green, "Create", () -> {
            if (busy) return;
            try {
                var name = WorldGit.sanitize(nameDraft.isEmpty() ? "my_world" : nameDraft);
                Main.INSTANCE.enterWorld(name, "home");
            } catch (RuntimeException ex) {
                status = ex.getMessage();
            }
        });
    }

    private static void drawClone(IGui gui) {
        gui.textHAlign("center");
        gui.textSize(0.02f);
        gui.textColor(focus.equals("url") ? ColorRGBA.Yellow : ColorRGBA.White);
        gui.text("Repo URL", 0.5f, 0.68f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) focus = "url";
            return true;
        });
        field(gui, urlDraft, 0.5f, 0.60f);

        gui.textColor(focus.equals("name") ? ColorRGBA.Yellow : ColorRGBA.White);
        gui.text("Folder name (blank = repo name)", 0.5f, 0.50f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) focus = "name";
            return true;
        });
        field(gui, nameDraft.isEmpty() ? "_" : nameDraft, 0.5f, 0.42f);
        captureKeys(gui, false);

        glass(gui, 0.38f, 0.28f, 0.22f, 0.08f, ColorRGBA.Gray, "Back", () -> {
            if (!busy) GameState.navigateTo("launcher");
        });
        glass(gui, 0.64f, 0.28f, 0.22f, 0.08f, ColorRGBA.Green, "Download", () -> Launcher.cloneAsync());
    }

    private static void cloneAsync() {
        if (busy) return;
        final var url = urlDraft.trim();
        final var explicit = nameDraft.trim();
        busy = true;
        status = "Cloning...";
        Thread.ofVirtual().start(() -> {
            try {
                var name = explicit.isEmpty() ? WorldGit.nameFromUrl(url) : WorldGit.sanitize(explicit);
                WorldGit.cloneWorld(url, name);
                Main.INSTANCE.enqueue(() -> {
                    busy = false;
                    status = "";
                    Main.INSTANCE.enterWorld(name, "home");
                    return null;
                });
            } catch (Exception ex) {
                Main.INSTANCE.enqueue(() -> {
                    busy = false;
                    status = ex.getMessage();
                    return null;
                });
            }
        });
    }

    private static void pullAsync(String name) {
        if (busy) return;
        busy = true;
        status = "Updating " + name + "...";
        var dir = WorldGit.worldsRoot().resolve(name);
        Thread.ofVirtual().start(() -> {
            try {
                WorldGit.pull(dir);
                Main.INSTANCE.enqueue(() -> {
                    busy = false;
                    status = name + " updated";
                    return null;
                });
            } catch (Exception ex) {
                Main.INSTANCE.enqueue(() -> {
                    busy = false;
                    status = ex.getMessage();
                    return null;
                });
            }
        });
    }

    private static void captureKeys(IGui gui, boolean nameOnly) {
        if (Master.inputPlugin == null) return;
        gui.textSize(0.01f);
        Master.inputPlugin.input("", -1f, -1f, evt -> onKey(evt, nameOnly), true);
    }

    private static void onKey(KeyInputEvent evt, boolean nameOnly) {
        if (!evt.isPressed()) return;
        var target = (!nameOnly && "url".equals(focus)) ? urlDraft : nameDraft;
        int code = evt.getKeyCode();
        String next = target;
        switch (code) {
            case KeyInput.KEY_BACK, KeyInput.KEY_DELETE -> {
                if (!target.isEmpty()) next = target.substring(0, target.length() - 1);
            }
            case KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER -> {
                return;
            }
            case KeyInput.KEY_V -> {
                return;
            }
            default -> {
                char c = evt.getKeyChar();
                if (c >= 32 && c < 127) next = target + c;
            }
        }
        if (!nameOnly && "url".equals(focus)) urlDraft = next;
        else nameDraft = next;
    }

    private static void field(IGui gui, String value, float x, float y) {
        gui.imageSize(0.56f, 0.06f)
                .imageAlpha(true)
                .imageColor(ColorRGBA.fromRGBA255(0, 0, 0, 160))
                .imageHAlign("center")
                .imageVAlign("center");
        gui.image("/cc0/white_pixel.png", x, y, false);
        gui.textHAlign("center");
        gui.textVAlign("center");
        gui.textSize(0.018f);
        gui.textColor(ColorRGBA.White);
        var shown = value.length() > 48 ? value.substring(value.length() - 48) : value;
        gui.text(shown + "_", x, y, false);
    }

    private static void dim(IGui gui) {
        gui.imageSize(1f, 1f)
                .imageAlpha(true)
                .imageColor(ColorRGBA.fromRGBA255(0, 0, 0, 170))
                .imageHAlign("center")
                .imageVAlign("center");
        gui.image("/cc0/white_pixel.png", 0.5f, 0.5f, false);
    }

    private static void card(IGui gui) {
        gui.imageSize(0.78f, 0.86f)
                .imageAlpha(true)
                .imageColor(ColorRGBA.fromRGBA255(12, 16, 22, 230))
                .imageHAlign("center")
                .imageVAlign("center");
        gui.image("/cc0/white_pixel.png", 0.5f, 0.5f, false);
    }

    private static void glass(IGui gui, float x, float y, float w, float h, ColorRGBA tint, String label, Runnable action) {
        gui.imageSize(w, h)
                .imageAlpha(true)
                .imageColor(tint)
                .imageHAlign("center")
                .imageVAlign("center");
        gui.image("/cc0/bw_glass_tab.png", x, y, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) action.run();
            return true;
        });
        gui.textHAlign("center");
        gui.textVAlign("center");
        gui.textSize(Math.min(0.02f, h * 0.45f));
        gui.textColor(ColorRGBA.White);
        gui.text(label, x, y, false);
    }
}