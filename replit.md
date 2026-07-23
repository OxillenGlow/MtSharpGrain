# MtSharpGrain

A sandbox Java desktop game built on [jMonkeyEngine](https://jmonkeyengine.org) with LWJGL3. Features semi-smooth voxel meshes, a GraalVM-powered JavaScript mod system, and a (partially deprecated) visual scripting editor.

---

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Build | Gradle 8.6 (wrapper: `./gradlew`) |
| Engine | jMonkeyEngine 3.9.0-stable + LWJGL3 |
| Scripting | GraalVM Polyglot / GraalJS 24.1.1 |
| GUI | Custom IGui (immediate-mode, jME3) |
| Visual scripting | jVisualScripting (deprecated / being replaced by JS mods) |

---

## How to build

```bash
# Compile and produce a runnable distribution
./gradlew installDist

# Output: build/install/MtSharpGrain/
# Launch on Linux: build/install/MtSharpGrain/bin/MtSharpGrain
```

> **Note:** Requires Java 21. The Nix package `jdk21` is installed in this repl.
> The game opens a native LWJGL3 window — it cannot be previewed in the Replit browser pane.

---

## Project layout

```
src/main/java/com/mtsharpgrain/   ← Core game (Main, WorldAccess, RenderManager, Player)
src/main/java/com/mtsharpgrain/js/  ← JavaScript mod bridge & world-gen runner
src/main/java/com/mtsharpgrain/node/ ← Block & command registries
src/main/java/com/mtsharpgrain/mlp/  ← MLP stub (unused/experimental)
src/main/java/com/jme/igui/          ← Immediate-mode GUI library
src/main/java/com/jvisualscripting/  ← Visual scripting engine (being deprecated)
assets/                               ← Textures, UI images, default JS mods
worlds/my_world/                      ← Runtime world data & user mods
```

---

## Development Plan

### 1 — Fix the build toolchain (blocker)

**Problem:** Gradle requires Java 21 but the environment's default JVM is Java 19 (GraalVM CE 22.3.1). The `jdk21` Nix package is installed but Gradle's toolchain auto-detection doesn't find it automatically.

**Fix options (pick one):**
- **Option A — tell Gradle where JDK 21 is:**
  Add to `gradle.properties`:
  ```
  org.gradle.java.installations.paths=/nix/store/<jdk21-path>
  ```
  Run `find /nix/store -maxdepth 1 -name "*jdk21*" -type d` to get the path.
- **Option B — lower the toolchain requirement to Java 19:**
  Change both `languageVersion = JavaLanguageVersion.of(21)` lines in `build.gradle` to `19`. GraalVM CE 22.3.1 (Java 19) is already on PATH.

---

### 2 — Known bugs and TODOs

| Location | Issue | Priority |
|---|---|---|
| `Main.java:223` | `Master.tic(…)` — misspelling intentionally left. Harmless but confusing. | Low |
| `WorldAccessor.java:8` | TODO: "write a real implementation … against your chunk manager" — currently a stub | High |
| `JSModifier.java:78` | Errors routed to `stderr` instead of a real logger — mod errors are silent in release builds | Medium |
| `TTFConverter.java:40` | Uses AWT for font conversion — problematic on headless servers | Low |
| `VisualScriptingEditor.java:60` | No "unsaved changes" prompt before closing | Low |
| `GameMlp.java` | Entire class is a stub (`TODO: replace with real rows`) — unused | Low (remove or implement) |
| `Main.java:184` | `ScriptRunner.loadAndExecuteVisualScript()` is commented out | Low |
| `Main.java` | World seed is hardcoded to `1234L` — should be configurable | Medium |

---

### 3 — JavaScript mod API gaps

The JS API exposed to mods (`JsApiBootstrap`) is mostly working but missing:

- **Error reporting back to mod authors** — right now a JS exception silently kills the mod's tick. Surface errors in the in-game console.
- **`onBlockChange` cancel semantics** — the listener exists but returning `false` to cancel a placement isn't clearly documented or tested.
- **Mod reload at runtime** — restarting the whole game to pick up mod changes slows iteration. A `!reload` console command that re-runs `ModPackManager.load()` would help a lot.
- **Sandboxing** — mods currently have full GraalVM Polyglot access; no filesystem or network restrictions. Add a `Context` allowlist.

---

### 4 — World / chunk system

- `WorldAccessor` is a stub — `WorldAccess` does the real work but the abstraction layer isn't finished. Completing this would decouple chunk logic from the JS world-gen thread.
- Chunk serialisation (`ChunkBinaryIO`) uses raw binary — no versioning header. A future format change will silently corrupt existing saves. Add a magic number + version byte at the front.
- The async generation thread (`js-chunk-gen`) has no back-pressure or queue limit — rapid player movement could queue thousands of chunks.

---

### 5 — Render / performance

- `RenderManager` loads chunks asynchronously but merges geometry on the main thread. For large view distances this causes frame hitches. Consider double-buffering the mesh upload.
- The floating-origin system in `Main.java` prevents precision issues — verify it's applied to physics objects (`jBullet`) as well as scene nodes.

---

### 6 — Visual scripting (jVisualScripting)

The README marks this as "deprecating". The cleanest path forward:
1. Keep the engine code compiling (it's referenced) but gate the editor behind a `--enable-jvs` flag.
2. Expose a JS API equivalent for every jVS node type so mod authors have no reason to use jVS.
3. Remove jVS in a later cleanup pass once the JS API is feature-complete.

---

### 7 — Build & packaging

- The `jpackage` Gradle task produces a native installer (`.deb` on Linux, `.dmg` on macOS, `.exe` on Windows). It works but requires `jpackage` to be on PATH — document this requirement.
- Consider adding a **fat JAR** task (`shadowJar` plugin) for simpler distribution without the full `installDist` folder structure.
- CI (`.github/workflows/`) exists — verify it points at a Java 21 action runner.

---

## User preferences

*(Add any preferences here as the project evolves)*
