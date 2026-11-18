package com.mtsharpgrain.gui;
import com.jme.igui.IGui;
import com.jme.igui.IGuiMouseEvent;
import com.jme3.input.KeyInput;
import com.jme3.math.ColorRGBA;
import com.mtsharpgrain.Main;
import com.mtsharpgrain.storage.WorldGit;
import java.util.HashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.function.Consumer;
public final class Launcher {
    private Launcher() {}
    private static final int PAGE = 7;
    private static int page;
    private static volatile String status = "";
    private static volatile boolean busy;
    private static String nameDraft = "";
    private static String urlDraft = "";
    private static HashMap<String, Boolean> active;
    private static boolean hoverFolder;
    private static boolean hoverNewWorld;
    private static boolean hoverGithub;
    private static String deleteConfirmName = "";
    public static void draw(IGui gui) {
        if (active == null) active = new HashMap<>();
        var path = GameState.guiState;
        card(gui);
        gui.textFont("DotGothic16/DotGothic16.fnt");
        gui.textHAlign("center");
        gui.textVAlign("center");
        gui.textColor(ColorRGBA.White);
        gui.textSize(0.03f);
        gui.text("Mt. SharpGrain", 0.5f, 0.86f, false);
        gui.textSize(0.022f);
        gui.textColor(ColorRGBA.fromRGBA255(180, 190, 200, 255));
        gui.text(status == null ? "" : status, 0.5f, 0.18f, false);
        if ("launcher/new".equals(path)) drawNew(gui);
        else if ("launcher/clone".equals(path)) drawClone(gui);
        else if ("launcher/delete".equals(path)) drawDelete(gui);
        else drawList(gui);
    }
    private static void drawList(IGui gui) {
        var worlds = WorldGit.listWorlds();
        int pages = Math.max(1, (worlds.size() + PAGE - 1) / PAGE);
        if (page >= pages) page = pages - 1;
        int from = page * PAGE;
        int to = Math.min(worlds.size(), from + PAGE);

        // A soft pixel-texture panel keeps the world list visually grouped.
        pixelPanel(gui, 0.50f, 0.50f, 0.76f, 0.65f, new ColorRGBA(0.10f, 0.13f, 0.17f, 0.78f));
        gui.textHAlign("left");
        gui.textVAlign("center");
        gui.textSize(0.026f);
        gui.textColor(ColorRGBA.White);
        gui.text("worlds:", 0.15f, 0.78f, false);

        if (worlds.isEmpty()) {
            gui.textSize(0.022f);
            gui.textColor(ColorRGBA.Gray);
            gui.text("No worlds yet. Create one below or clone one from GitHub.", 0.17f, 0.70f, false);
        }

        float y = 0.70f;
        for (int i = from; i < to; i++) {
            final String name = worlds.get(i);
            var dir = WorldGit.worldsRoot().resolve(name);
            boolean git = WorldGit.isGit(dir);
            pixelPanel(gui, 0.50f, y, 0.70f, 0.058f,
                    new ColorRGBA(0.78f, 0.86f, 0.94f, 0.13f));

            gui.textHAlign("left");
            gui.textVAlign("center");
            gui.textSize(0.025f);
            gui.textColor(ColorRGBA.White);
            gui.text(name + (git ? "  (git)" : ""), 0.17f, y, false);
            
            
            gui.textColor(ColorRGBA.Green);
            smallTextButton(gui, "Play", 0.55f, y, () -> {
                if (!busy) Main.INSTANCE.enterWorld(name, "home");
            });
            
            gui.textColor(ColorRGBA.White);
            smallTextButton(gui, "Mods", 0.65f, y, () -> {
                if (!busy) Main.INSTANCE.enterWorld(name, "home/modview");
            });
            if (git) smallTextButton(gui, "Up", 0.85f, y, () -> pullAsync(name));
            gui.textColor(ColorRGBA.Red);
            smallTextButton(gui, "Del", 0.75f, y, () -> {
                if (!busy) {
                    deleteConfirmName = name;
                    status = "";
                    GameState.navigateTo("launcher/delete");
                }
            });
            y -= 0.071f;
        }

        gui.textHAlign("center");
        gui.textSize(0.022f);
        gui.textColor(ColorRGBA.White);
        gui.text("<", 0.45f, 0.24f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT && page > 0) page--;
            return true;
        });
        gui.text((page + 1) + " / " + pages, 0.50f, 0.24f, false);
        gui.text(">", 0.55f, 0.24f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT && page + 1 < pages) page++;
            return true;
        });

        // Main actions are deliberately compact and anchored to the bottom-left.
        smallImageButton(gui, "/self/PlusB.png", 0.055f, 0.095f, () -> {
            if (!busy) {
                nameDraft = "";
                status = "";
                GameState.navigateTo("launcher/new");
            }
        }, () -> hoverNewWorld = true, () -> hoverNewWorld = false);
        smallImageButton(gui, "/self/Download.png", 0.105f, 0.095f, () -> {
            if (!busy) {
                nameDraft = "";
                urlDraft = "https://github.com/";
                status = "";
                GameState.navigateTo("launcher/clone");
            }
        }, () -> hoverGithub = true, () -> hoverGithub = false);
        smallImageButton(gui, "/self/FileB.png", 0.155f, 0.095f, () -> {
            if (!busy) com.tools.AiGeneratedTools.openFolder(WorldGit.worldsRoot().getParent());
        }, () -> hoverFolder = true, () -> hoverFolder = false);

        if (hoverNewWorld) tooltip(gui, "Create a new standard world", 0.055f, 0.145f);
        if (hoverGithub) tooltip(gui, "Clone a world from a GitHub repository", 0.105f, 0.145f);
        if (hoverFolder) tooltip(gui, "Open the worlds folder to manage worlds and plug-ins", 0.155f, 0.145f);
    }

    private static void drawDelete(IGui gui) {
        pixelPanel(gui, 0.50f, 0.50f, 0.58f, 0.34f, new ColorRGBA(0.12f, 0.14f, 0.18f, 0.94f));
        gui.textHAlign("center");
        gui.textVAlign("center");
        gui.textSize(0.030f);
        gui.textColor(ColorRGBA.White);
        gui.text("Delete world?", 0.50f, 0.62f, false);
        gui.textSize(0.022f);
        gui.textColor(ColorRGBA.fromRGBA255(210, 215, 225, 255));
        gui.text("Are you sure you want to permanently delete", 0.50f, 0.53f, false);
        gui.textColor(ColorRGBA.Yellow);
        gui.text(deleteConfirmName + "?", 0.50f, 0.48f, false);
        gui.textColor(ColorRGBA.Gray);
        gui.text("This cannot be undone.", 0.50f, 0.42f, false);
        gui.textColor(ColorRGBA.White);
        smallTextButton(gui, "Cancel", 0.40f, 0.33f, () -> GameState.navigateTo("launcher"));
        gui.textColor(ColorRGBA.Red);
        smallTextButton(gui, "Delete", 0.60f, 0.33f, () -> {
            if (!busy && !deleteConfirmName.isBlank()) {
                try {
                    deleteWorldTree(WorldGit.worldsRoot().resolve(WorldGit.sanitize(deleteConfirmName)));
                    status = deleteConfirmName + " deleted";
                    deleteConfirmName = "";
                    GameState.navigateTo("launcher");
                } catch (IOException | RuntimeException ex) {
                    status = "Delete failed: " + ex.getMessage();
                }
            }
        });
    }

    private static void deleteWorldTree(java.nio.file.Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (var path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void pixelPanel(IGui gui, float x, float y, float w, float h, ColorRGBA tint) {
        gui.imageSize(w, h).imageAlpha(true).imageHAlign("center").imageVAlign("center")
                .imageColor(tint);
        gui.image("/cc0/white_pixel.png", x, y, false);//its actually black despite the "white pixel" name
        gui.imageColor(ColorRGBA.White);
    }

    private static void smallImageButton(IGui gui, String image, float x, float y,
                                         Runnable action, Runnable onHover, Runnable onOut) {
        gui.imageSize(0.03f, 0.03f).imageAlpha(true)
                .imageHAlign("center").imageVAlign("center");
        gui.image(image, x, y, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_IN) onHover.run();
            else if (event == IGuiMouseEvent.MOUSE_OUT) onOut.run();
            else if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) action.run();
            return true;
        });
    }

    private static void smallTextButton(IGui gui, String label, float x, float y, Runnable action) {
        pixelPanel(gui, x, y, 0.060f, 0.034f, new ColorRGBA(0.80f, 0.88f, 0.96f, 0.22f));
        gui.textHAlign("center");
        gui.textVAlign("center");
        gui.textSize(0.023f);
        // text color left to caller (Play=green, Del=red, etc.)
        gui.text(label, x, y-0.01f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) action.run();
            return true;
        });
    }

    private static void tooltip(IGui gui, String message, float x, float y) {
        float width =  0.025f + message.length() * 0.015f;
        pixelPanel(gui, x + width * 0.5f, y+0.005f, width, 0.052f,
                new ColorRGBA(0.08f, 0.10f, 0.13f, 0.96f));
        gui.textHAlign("left");
        gui.textVAlign("center");
        gui.textSize(0.025f);
        gui.textColor(ColorRGBA.White);
        gui.text(message, x + 0.012f, y, false);
    }

    private static void drawNew(IGui gui) {
        gui.textHAlign("center");
        gui.textSize(0.022f);
        gui.textColor(ColorRGBA.White);
        gui.text("New world name", 0.5f, 0.62f, false);
        field(gui, nameDraft, s -> nameDraft = s, 0.5f, 0.52f,
              "enter world name here");
        glass(gui, 0.38f, 0.36f, 0.22f, 0.08f, ColorRGBA.Gray, "Back", () -> {
            if (!busy) GameState.navigateTo("launcher");
        });
        gui.imageSize(0.10f, 0.065f)
                .imageAlpha(true)
                .imageHAlign("center")
                .imageVAlign("center");
        gui.image("/self/DoneB.png", 0.62f, 0.36f, (event, arg) -> {
            if (event != IGuiMouseEvent.MOUSE_PRESSED_LEFT || busy) return true;
            try {
                var name = WorldGit.sanitize(nameDraft.isEmpty() ? "world" : nameDraft);
                Main.INSTANCE.enterWorld(name, "home");
            } catch (RuntimeException ex) {
                status = ex.getMessage();
            }
            return true;
        });
    }
    private static void drawClone(IGui gui) {
        gui.textHAlign("center");
        gui.textSize(0.022f);
        gui.textColor(ColorRGBA.White);
        gui.text("Repo URL", 0.5f, 0.68f);
        field(gui, urlDraft, s -> urlDraft = s, 0.5f, 0.60f,
              "https://github.com/...");
        gui.textColor(ColorRGBA.White);
        gui.text("Folder name (blank = repo name)", 0.5f, 0.50f);
        field(gui, nameDraft, s -> nameDraft = s, 0.5f, 0.42f,
              "optional folder name");
        glass(gui, 0.38f, 0.28f, 0.22f, 0.08f, ColorRGBA.Gray, "Back", () -> {
            if (!busy) GameState.navigateTo("launcher");
        });
        gui.imageSize(0.10f, 0.065f)
                .imageAlpha(true)
                .imageHAlign("center")
                .imageVAlign("center");
        gui.image("/self/Download.png", 0.64f, 0.28f, (event, arg) -> {
            if (event == IGuiMouseEvent.MOUSE_PRESSED_LEFT) Launcher.cloneAsync();
            return true;
        });
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
    /**
     * Universal text field (console style).
     * Left-aligned so the blinking cursor never shifts the text.
     * Both fields stay registered; only the focused one accepts keys.
     */
    private static void field(IGui gui, String value, Consumer<String> setter,
                              float x, float y, String placeholder) {
        if (Master.inputPlugin == null) return;
        Launcher.active.putIfAbsent(placeholder, false);

        float left = x - 0.26f;

        // underline = visual + hit target
        gui.textHAlign("left");
        gui.textVAlign("center");
        gui.textSize(0.08f);
        gui.textColor(Launcher.active.get(placeholder)? ColorRGBA.Yellow :ColorRGBA.Gray);
        Master.inputPlugin.input(
            "_____________________________________",
            left, y,
            (mouse, arg) -> {
                if (mouse == IGuiMouseEvent.MOUSE_IN) {
                    active.put(placeholder, true);
                } else if (mouse == IGuiMouseEvent.MOUSE_OUT) {
                    active.put(placeholder, false);
                }
                return true;
            },
            e -> {
                if (!e.isPressed() || !Boolean.TRUE.equals(active.get(placeholder))) return;
                int code = e.getKeyCode();
                // always read the live draft
                String cur = switch (placeholder) {
                    case "https://github.com/..." -> urlDraft;
                    case "optional folder name" -> nameDraft;
                    default -> nameDraft; // "enter world name here"
                };
                switch (code) {
                    case KeyInput.KEY_BACK, KeyInput.KEY_DELETE -> {
                        if (!cur.isEmpty()) setter.accept(cur.substring(0, cur.length() - 1));
                    }
                    case KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER -> {}
                    default -> {
                        char c = e.getKeyChar();
                        if (c >= 32 && c < 127) setter.accept(cur + c);
                    }
                }
            },
            false
        );

        // live text + blink (left-aligned → no shake)
        gui.textHAlign("left");
        gui.textVAlign("center");
        gui.textSize(0.02f);
        var flash = Launcher.active.get(placeholder) && ((System.currentTimeMillis() / 200) % 2 == 1) ? "_" : " ";
        if (value.isEmpty()) {
            gui.textColor(ColorRGBA.Gray);
            gui.text(placeholder + flash, left, y, false);
        } else {
            gui.textColor(ColorRGBA.White);
            var shown = value.length() > 40 ? value.substring(value.length() - 40) : value;
            gui.text(shown + flash, left, y, false);
        }
    }
    
    private static void card(IGui gui) {
        gui.imageSize(2f, 0.9f)
                .imageAlpha(true)
                .imageHAlign("center")
                .imageVAlign("center");
        gui.image("/self/bkgl.png", 0.8f, 0.5f, false);
        gui.image("/self/bkgl.png", 0.8f, 0.5f, false);
    }
    private static void glass(IGui gui, float x, float y, float w, float h, ColorRGBA tint, String label, Runnable action) {
        gui.imageSize(w, h)
                .imageAlpha(true)
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