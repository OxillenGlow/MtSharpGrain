# AGENTS.md

Project handoff and working notes for AI coding agents.

**Project:** MtSharpGrain  
**Status:** Beta prototype (`0.1.1-beta`)  
**Last reviewed:** 2026-09-25

## Start here

1. Read this file completely before changing code.
2. Run `git status --short` and check the current diff.
3. Inspect the relevant subsystem rather than guessing from filenames.
4. Preserve unrelated changes; this repository may contain uncommitted work.
5. After a task, update the relevant status and handoff sections below.

## Project overview

MtSharpGrain is a Java 21 sandbox game built on jMonkeyEngine 3.9. It uses non-voxel/rounded node meshes, procedural terrain, a custom JavaScript mod system, and a custom `IGui` interface.

The application entry point is:

```text
src/main/java/com/mtsharpgrain/Main.java
```

Build configuration:

```text
build.gradle
settings.gradle
```

The `assets` Gradle subproject supplies runtime assets. Generated/runtime directories such as `build/`, `bin/`, and `worlds/` must not be edited as source.

## Architecture

### Application and input

- `Main.java` owns application startup, the render loop, camera, lighting, GUI, mod loading, and shutdown.
- `TestInit.java` creates baseline lights, initializes the PyBall mesh, and installs the `F`-key fly-camera toggle.
- `gui/Master.java` owns menu rendering and path-like UI states: `home`, `play`, and `home/modview/...`.
- `gui/Console.java` captures system output and provides the in-game console.
- `gui/GameState.java` stores navigation and play/menu state.
- `node/Check.java` handles mouse block placement, removal, and mod spatial clicks.
- `BlockSelector.java` performs raycasts and converts collision points into block coordinates.
- `newton/PhysicsControl.java` and `newton/FlyCamPhysicsControl.java` are experimental physics code. The main game currently uses JME `FlyByCamera`; do not assume the physics module is wired in.

### World and chunks

- `BufferedChunk.java` stores a `16 x 16 x 16` `int` block array.
- `ChunkPos.java` identifies chunk coordinates. World-to-chunk conversion uses `>> 4`.
- `WorldAccess.java` owns chunk loading, generation, block edits, saving, validation, and committed-change tracking.
- `ChunkFileHelper.java` reads and writes compressed chunk files.
- `ChunkBinaryIO.java` and `tools/ChunkZipper.java` provide flattened/binary chunk helpers and deflate compression.
- `RenderManager.java` streams chunks around the player, marks dirty chunks, builds meshes asynchronously, and attaches them on the JME render thread.
- `ChunkMeshBuilder.java` creates geometry/materials for PyBall, cube, tiny, tall, flat, stool, and plant-pod blocks.
- `PyBallJmeMesh.java` implements the rounded connected-node mesh.

The world seed is currently hardcoded in `Main.java` (`1234L`), and the default spawn is hardcoded near `(10000, 16, 0)`.

### Blocks, inventory, and persistence

- `node/BlockRegistry.java` defines built-in block IDs, colors, shininess, and mesh-builder types.
- `node/DynamicBlockRegistry.java` persists mod-registered blocks in `worlds/<world>/registered-blocks.xml`. Dynamic IDs currently start at negative values.
- `storage/BlockValuesStore.java` stores per-block key/value data in `worlds/<world>/chunk-values/`.
- `gui/Inventory.java` gates ordinary block placement/removal and persists inventory to `inventory.xml`.
- `WorldAccess.forceSetBlockAt()` intentionally bypasses mod validators and inventory checks. Only use it for trusted/internal paths.
- Chunk writes and most XML/property writes use a temporary file plus atomic move where supported.

### Procedural generation

`src/main/resources/chunkgen.js` is the default world generator. `JsChunkGenerator` loads it into one Graal JavaScript context and one dedicated `js-chunk-gen` thread.

The generator uses deterministic hash-based randomness, layered trigonometric heightmaps, sparse mountains/slashes, polar ice, ores, template swaps, and rare glass/grass sphere structures. Default generation code is extracted to the selected world by `Main.extractFiles()`.

### JavaScript mod system

Runtime code is in:

```text
src/main/java/com/mtsharpgrain/js/
src/main/java/com/mtsharpgrain/js/mainthread/
```

Key files:

- `ModPackManager.java` — discovers, loads, enables, disables, and reloads mod packs.
- `JSModifier.java` — owns one pack's JavaScript runtime and lifecycle.
- `ModBridge.java` — per-pack mailbox for work submitted to the owning thread.
- `EngineAccess.java` — gateway for jME operations from mod threads.
- `JsApiBootstrap.java` — creates the per-pack Graal context and binds the JavaScript globals.
- `TickRegistry.java` — tagged per-tick JavaScript callbacks.
- `BlockChangeRegistry.java` — validators for block edits.
- `GuiApi.java` / `EngineGuiApi.java` — JavaScript GUI elements and click dispatch.
- `MatrixApi.java` — dynamic block registration and per-block values.
- `TimerManager.java` — JavaScript `setInterval`/`setTimeout` support.
- `MessagingApi.java` / `ModBridge.java` — inter-mod communication.
- `DataApi.java` — per-pack saved key/value data.

JavaScript globals currently include:

```text
Block, Scene, Gui, Data, Mod, Engine, Matrix, Player, Inventory
```

Pack naming matters:

- A directory name ending in `MAIN` runs on the render thread and is Update-only.
- Other packs run on a virtual thread and use the mailbox loop.
- A pack directory is a mod pack; scripts inside it are loaded in sorted order.
- `LFT*`, `RHT*`, `BTM*`, `UTIL*`, and `MODE*` packs are treated as always-on for GUI drawing.

## Important threading and safety invariants

- Never touch a Graal context from a thread other than its owning mod thread.
- Background pack work must go through `ModBridge`.
- jME scene mutation must go through `EngineAccess` so it runs on the render thread.
- `EngineAccess.call()` waits for the render thread; `post()` is fire-and-forget.
- Ordinary block edits go through `WorldAccess.requestBlockChange()` / `processPendingBlockChanges()` so mod validators and inventory checks can run.
- `forceSetBlockAt()` skips validation and inventory protections.
- GUI drawing is Java/render-thread work; JavaScript GUI callbacks are dispatched back to the owning pack.
- JavaScript has explicit host access only; arbitrary Java class lookup is disabled.
- Do not add direct cross-thread calls to jME objects as a shortcut.

## Common commands

```bash
# Run the game
./gradlew run

# Compile/test (requires a Java 21 toolchain)
./gradlew test
./gradlew build

# Build the cross-platform distribution
./gradlew distZip

# Build a native package
./gradlew jpackage
```

On macOS the Gradle configuration adds `-XstartOnFirstThread` where required. The current machine may not have a local Java 21 toolchain; GitHub Actions uses Java 21.

## Current known issues and priorities

Review before changing behavior:

1. Block-edit readiness logic in `WorldAccess` has unused readiness helpers and appears to delay edits until an age-based threshold.
2. The block-edit worker is launched from a virtual thread even though comments describe the commit path as render-thread-owned; verify thread affinity before changing it.
3. `ChunkMeshBuilder` can dereference a missing static block definition for some unknown positive block IDs.
4. `Inventory` is documented as render-thread-only, but block-edit processing may access it from a worker thread.
5. `DynamicBlockRegistry` and `BlockValuesStore` are singletons, which may complicate switching worlds in one JVM.
6. World seed, spawn, and world selection are not persisted/configurable.
7. The `newton` physics system is not currently integrated into the main game.
8. There are no meaningful automated tests under `src/test/java`; files under `src/main/java/test/` are manual experiments.
9. `README.md` contains a roadmap with unfinished GUI, physics, NPC, multiplayer, performance, and world-selection work.

## Working rules

- Keep edits scoped to the requested subsystem.
- Prefer small, reviewable changes and preserve existing style.
- Do not edit generated `build/`, `bin/`, `.gradle/`, or `worlds/` content.
- Do not rewrite or reformat vendored code under `src/main/java/com/jme`, `com/jvisualscripting`, or `org/json` unless explicitly requested.
- When touching the mod runtime, update the threading documentation/comments alongside the code.
- When changing block IDs, update `BlockRegistry`, its names, and any affected inventory/mesh paths.
- When changing chunk storage or generation, check both synchronous block-edit generation and asynchronous render streaming.

## Handoff checklist

When handing work to another agent or ending a session:

- Record what changed and which files were touched.
- Record the exact verification command and result.
- Note any remaining bug, uncertainty, or unverified assumption.
- Update this file's current-state section if the architecture or known issues changed.
- Keep the repository diff focused; do not commit unrelated generated files.
