# MtSharpGrain JS Modding

### Why mods matter

The base game is intentionally small — a voxel sandbox with a handful of
block types and no built-in quests, or economy. (at present)

This is because:
- I am a single person and do not have the resources to make a full game
- I will **not** be able to make my game fit everyone's tast.

Modding solves both as **you** can make your own game without messing with boring parts. This is more true with AI as now **ANYONE** can make a mod without fancy coding skills (to those AI haters: yes, AI is not magical, but it is a great tool)

Your imagination is *literally* the limit (and my API).

> [!TIP]
> **Using an AI to help write mods:** paste the "Essential API Reference for
> AI context" block near the bottom of this page into your prompt along with
> what you want built. It's a condensed version of every global with just
> enough signature/behavior info for an AI to write correct code without
> hallucinating methods that don't exist — much more reliable than pasting
> this whole wiki page.

> [!IMPORTANT]
> There are already some mods that come as a default. This means that your mod will be stuffed in a game with other mods, you should first look at the javascript messaging convention the existing mods have *unless* you plan on deleting the existing mods. They also serve as some good examples [[link to code|https://github.com/OxillenGlow/MtSharpGrain/tree/main/src/main/resources/mods]]|[[link to wiki page on default mods|Default js mods]]

---

###### Details and code, click to expand:
<details>
<summary><strong>Structure</strong></summary>

Scripts are organized into **mod packs** — each top-level subfolder under
`worlds/my_world/mod/` gets its own **isolated GraalVM Context**. All `.js`
files within a pack's folder (including subfolders) are loaded recursively
on startup, sorted alphabetically. One bad script does not block others in
the same pack, or any other pack — errors are logged and that script is
skipped.

If you are new to JavaScript: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide or any other place to learn basic JavaScript.

---

## File placement & pack isolation

```
worlds/
  my_world/
    chunkgen.js           ← Special file for generating the world, 
    mod/
      loose.js            ← NOT loaded (no pack folder = skipped)
      ui/                 ← mod pack folder "ui" — has its own Context
        hud.js            ← loaded into ui's context
        inventory.js      ← loaded into ui's context
        widgets/
          button.js       ← still ui's context (subfolders included)
      gameplay/           ← mod pack "gameplay" — separate Context
        movement.js       ← isolated from ui entirely
        blocks.js
```

see [here](https://github.com/OxillenGlow/MtSharpGrain/wiki/2.1-Code/#worldgenerator) for more on chunkgen.js

- **Subdirectories only.** A `.js` file placed directly in `mod/` (not inside
  a subfolder) is **not loaded**. Every pack needs its own folder.
- **One Context per pack.** `ui` and `gameplay` above don't share globals,
  variables, or state. A crash or infinite loop in one pack does not affect
  another.
- **Pack load order.** Packs are initialized alphabetically by folder name
  (`gameplay` before `ui`).
- **File load order within a pack.** Alphabetical, recursive through
  subfolders — same rule as before, just scoped to one pack now.
- **Init-order dependencies.** If a script needs a global set by another
  script *in the same pack*, prefix filenames: `00_lib.js`, `01_feature.js`.
  This no longer works across packs — see below.

> [!TIP]
> Globals don't leak between packs, so naming conflicts across different
> mods are no longer a concern the way they used to be.
>
> In pack `ui/`:
> ```js
> globalThis.Toolbar = { ... };
> ```
> In pack `gameplay/`:
> ```js
> globalThis.Toolbar = { ... }; // totally separate, no collision
> ```
>
> Within a single pack, the old advice still applies — use one global slot
> per script to avoid stepping on your own other scripts:
> ```js
> globalThis.ExampleName = {
>     getSomething() { return 42; }
> };
> ```

</details>

<details>
<summary><strong>Pack ordering & always-visible packs (LFT / RHT / BTM / MODE / UTIL)</strong></summary>

Normally, a pack's `Gui` elements are only drawn while the player is on the
`home` screen menu — the moment you enter `play` (flying/building), every
pack's GUI is hidden, and while browsing `home/modview/<pack>` only *that*
pack's GUI is shown (so it can't cover the "< Back" button).

Packs whose **folder name starts with one of these prefixes** are exempt
from that gating and draw on **both `home` and `play`**, all the time:

| Prefix | Intended use                                  |
|--------|------------------------------------------------|
| `LFT`  | Persistent left-edge HUD element                |
| `RHT`  | Persistent right-edge HUD element                |
| `BTM`  | Persistent bottom-edge HUD element                |
| `MODE` | Persistent mode/state indicator (anywhere on screen) |
| `UTIL` | Persistent utility overlay (anywhere on screen)   |

The prefix only controls **whether the pack is allowed to draw at all** —
it does *not* pin your GUI elements to that edge for you. `Gui.guiWord`
still takes whatever `x, y` you give it. A pack named `LFTcoords` that calls
`Gui.guiWord("...", 0.9, 0.9, ...)` will happily draw in the top-right; the
name is just a promise you're making to yourself and other mod authors.

Example folder names: `mod/LFTsidebar/`, `mod/RHTminimap/`, `mod/BTMhotbar/`.

> [!WARNING]
> **Play nice with other always-on packs.** `LFT`/`RHT` packs should keep
> their elements within roughly the **left/right 20% of the screen width**,
> and `BTM` packs within a similarly modest bottom strip. Since always-on
> packs draw across *every* screen including gameplay, a sprawling HUD from
> one mod will visually stack with every other player's installed always-on
> mods and make the screen unreadable. Keep the element count low and the
> footprint small — a coordinate readout or a hotbar strip, not a full
> dashboard.
>
> Also worth remembering: `setEnabled(packName, false)` from the mods menu
> still fully disables an always-on pack (tick/draw/click all stop) — the
> prefix bypasses the *screen-path* gating, not the enable/disable toggle.
> Players annoyed by a heavy always-on HUD can always turn it off there.

Non-prefixed packs are unaffected by any of this — normal `home`-only /
`modview`-only gating applies exactly as described in the rest of this
page.

</details>

---

<details>
<summary><strong>API</strong></summary>

## Globals

Seven objects are injected into every script automatically, fresh per pack
(each pack gets its own instances, not shared references).
You cannot access arbitrary Java classes — only what is listed here.

| Global   | Purpose                                              |
|----------|-------------------------------------------------------|
| `Scene`  | Create/move/rotate/destroy 3D objects in the world      |
| `Block`  | Read and edit voxel blocks                             |
| `Inventory` | Access player inventory, great for crafting and special use blocks | 
| `Engine` | Register per-tick callbacks and block-change rules      |
| `Gui`    | Create screen-space text elements                       |
| `Player` | Read/set the player camera's position                   |
| `Data`   | Save/load simple per-pack XML save data                 |
| `Mod`    | Send messages to other loaded packs                     |
| `Matrix` | Register custom blocks and store per-block key/value data |

Note: `Scene` node handles, `Gui` element handles, and tick tags are only
valid **within the pack that created them**. A handle created in `gameplay`
means nothing to a script in `ui` — there's no cross-pack handoff mechanism
for handles. (For actual cross-pack data exchange, see `Mod` below.)

</details>

---

<details>
<summary><strong>Scene — create, move, rotate, destroy 3D objects</strong></summary>

All Scene methods operate on **handles** — opaque numbers that refer to a
scene node. Never try to store or manipulate the underlying Java object;
always go through the handle.

Handle `0` is the world root node in **every pack's context** — each pack
sees the same root node, so objects from different packs still end up in
the same visible scene graph, they just can't reference each other's handles.

> [!TIP]
> For more on how nodes and scene graph works, go to [scene graph in jMonkeyEngine explanation](https://wiki.jmonkeyengine.org/docs/3.9/tutorials/concepts/scenegraph_for_dummies.html)

```js
// createNode(name) → handle
const group = Scene.createNode("myGroup");

// createCube(name, size) → handle
// size is the full edge length, e.g. 1.0 = a standard voxel-sized cube
const cube = Scene.createCube("myCube", 1.0);

// attachChild(parentHandle, childHandle)
Scene.attachChild(0, group);       // attach group to world root
Scene.attachChild(group, cube);    // attach cube under group

// setPosition(handle, x, y, z)
Scene.setPosition(cube, 10, 5, 0);

// getPosition(handle) → [x, y, z]  (world position, not local)
const p = Scene.getPosition(cube);
const wx = p[0], wy = p[1], wz = p[2];

// setRotation(handle, xRad, yRad, zRad) — Euler angles in RADIANS,
// order (pitch=x, yaw=y, roll=z)
Scene.setRotation(cube, 0, Math.PI / 2, 0); // 90° yaw

// getRotation(handle) → [xRad, yRad, zRad]  (local rotation, same order as setRotation)
const r = Scene.getRotation(cube);
const pitch = r[0], yaw = r[1], roll = r[2];

// setColor(handle, r, g, b, a)   — values 0..1
Scene.setColor(cube, 1, 0, 0, 1); // red

// destroy(handle)  — removes from scene and frees the handle
Scene.destroy(cube);

// getBlockId(x, y, z) → int  — read-only voxel query at integer block coords
const id = Scene.getBlockId(10, 4, 0);

```

#### Other methods:

```js
Scene.createRectangle(String name, float x, float y, float z);

Scene.createLight(String name, float r, float g, float b, float radius);

Scene.setLightColor(long handle, float r, float g, float b); // normal setcolor doesn't work for light

Scene.setLightRadius(long handle, float radius); // same as strength of the light
```

**Spatial Click Events**

Mods can react when a player clicks something that *isn't* voxel terrain — e.g. a cube spawned with `Scene.createCube`. Register a handler with `Engine.onSpatialLeftClick(fn)` or `Engine.onSpatialRightClick(fn)`.

A simple spin-in-place using `setRotation` in a tick callback:

```js
let angle = 0;
Engine.onTick(function(tpf, tag) {
    angle += tpf; // radians per second
    Scene.setRotation(cube, 0, angle, 0);
}, "Update");
```

```
let target = Scene.createCube("myTarget", 1);
Scene.setPosition(target, 5, 2, 5);

Engine.onSpatialLeftClick(function(name) {
    if (name === "myTarget") {
        Scene.setColor(target, 0, 1, 0, 1); // turns green when clicked
    }
});
```

</details>

<details>
<summary><strong>Block — read and edit voxel blocks</strong></summary>

Read and write voxel blocks. Changes trigger a mesh rebuild for the affected
chunk and its neighbors automatically. `Block` calls affect the shared world
state — this is one of the few things that *is* effectively cross-pack,
since there's only one world.

```js
// get(x, y, z) → int
const id = Block.get(10, 4, 0);

// place(x, y, z, blockId)
Block.place(10, 5, 0, 2); // place stone NOTE! this notifies other mods.
// It means it might takes out blocks from the player! if player doesn't have enough blocks or cant place, bridge will not work

// destroy(x, y, z)
Block.destroy(10, 5, 0);  // set to air 

// forceSet(x, y, z, blockId)
Block.forceSet(10, 5, 0, 2); // Bypasses notifying other mods

```

A `Block.place`/`Block.destroy` call can be **rejected** — see
[Engine.onBlockChange](#engine--tick-callbacks--block-validation) below. This
runs validators from *every loaded pack*, not just your own. A rejected call
throws; if it happens inside an `Engine.onTick` callback, that counts toward
the callback's failure count (see Engine).

If you don't want that, use Block.forceSet()

> [!IMPORTANT]
> Scene.getBlockId(10, 4, 0); and Block.get(10, 4, 0); are **the same** you can use interchangeably 

### Block IDs

| ID | Name         |
|----|--------------|
| 0  | Air          |
| 2  | Stone        |
| 3  | Dirt         |
| 4  | Grass        |
| 5  | Crystal Ore  |
| 6  | Ice Sludge   |
| 7  | Silicon      |
| 8  | Sulfur       |
| 9  | Metal Block  |
| 10 | Glass        |

for the newest see [this java class](https://github.com/OxillenGlow/MtSharpGrain/blob/main/src/main/java/com/mtsharpgrain/node/BlockRegistry.java)

</details>

<details>
<summary><strong>Player — read/move the camera</strong></summary>

Read or move the player's camera directly. Shared across packs — there's
only one player/camera, same as with `Block`.

```js
// getPosition() → [x, y, z]
const p = Player.getPosition();
const px = p[0], py = p[1], pz = p[2];

// setPosition(x, y, z)  — teleports the camera
Player.setPosition(0, 20, 0);
```

`Player` wraps the game camera, not a separate physics body — moving it
moves the view instantly with no collision checks.

</details>

<details>
<summary><strong>Inventory — manage the player's held block items</strong></summary>

Track how many of each block type the player is carrying (limited by distinct TYPE count, not total items). The Inventory enforces pickups/spends when the world changes and provides a small JS API.

- MAX_TYPES: 20 distinct block types can be held at once.
- Persistence: in-memory map is written to worlds/<world>/inventory.xml
- If broken block is a new type and MAX_TYPES is reached, the break is rejected.

JS API (bound as global Inventory)
```js
// add(blockId, amount) → boolean
Inventory.add(2, 5);    // add 5 of block id 2; returns true on success, false on failure (amount<=0 or no room for a new type)

// remove(blockId, amount) → boolean
Inventory.remove(2, 1); // remove 1 of block id 2; returns false if amount<=0 or not enough held

// get(blockId) → int
const n = Inventory.get(2); // number held (0 if none)
```

Notes:

- add/remove return false for invalid amounts (<= 0) or when the operation would violate limits (e.g., exceeding MAX_TYPES or removing more than held).
- Block.place/Block.destroy may throw or be rejected when inventory rules prevent the change — this could happen even when validators across all loaded packs accept so you have to be careful.
</details>

<details>
<summary><strong>Engine — tick callbacks & block validation</strong></summary>

### Per-tick callbacks

Register a function to run on a specific tag group. Tags are scoped to the
pack that registers them — a `"placeStoneBtn"` tag in `gameplay/` and a
`"placeStoneBtn"` tag in `ui/` are two entirely separate registrations, each
only dispatched within its own pack's context.

```js
// Engine.onTick(fn, tag)
// fn receives (tpf, tag):
//   tpf  — time per frame in seconds (use for frame-rate independent movement)
//   tag  — the tag this callback was registered under

Engine.onTick(function(tpf, tag) {
    const p = Scene.getPosition(cube);
    Scene.setPosition(cube, p[0], p[1] + tpf, p[2]); // float upward
}, "Update");
```

**Only the `"Update"` tag runs automatically every frame, per pack.** Each
pack's `"Update"` group ticks once per frame, independently of every other
pack's `"Update"` group. If you register a callback under any other tag, it
will *never* fire on its own; it only runs when a `Gui` element in the
**same pack** sharing that exact tag is clicked (see below). If you want
per-frame logic, tag it `"Update"`. If you want a click handler, give it
whatever tag your button uses — don't tag a button `"Update"` unless you
also want it firing every single frame.

A tag can have multiple callbacks — all of them fire when that group ticks.
Callbacks that throw 5 times in a row are automatically disabled with an
error in the log.

### Validating block changes

Register a function that runs on every `Block.place` / `Block.destroy` call
(from any script in any pack, or from player-driven edits) and decides
whether it's allowed.

```js
// Engine.onBlockChange(fn)
// fn receives (x, y, z, blockId) and must return true (allow) or false (reject)

Engine.onBlockChange(function(x, y, z, blockId) {
    if (blockId === 9 && y > 200) return false; // no metal above y=200
    return true;
});
```

If a validator returns `false` — or throws — the change is rejected and the
call that triggered it (`Block.place`, `Block.destroy`, or a player edit)
throws instead of applying. **This applies across every pack**: your
validator gets a say on edits made by scripts in other packs too, and their
validators get a say on yours. Multiple validators can be registered across
all packs; the first one to reject wins. There's no way to know *which*
validator (or which pack) rejected a given change from JS — write
validators to log their own rejections if you need that.

</details>

<details>
<summary><strong>Gui — screen-space text elements</strong></summary>

Create screen-space text. Coordinates are **normalized**: `0,0` = bottom-left,
`1,1` = top-right. Draw order matches the element list — elements added later
draw on top, **within the same pack**. Elements from different packs are
drawn pack-by-pack (in the same alphabetical order packs load in), not
interleaved by creation time across packs. `toTop`/`toBottom` only reorder
within the calling pack's own element list.

`guiWord` is **upsert by tag, scoped to the pack**. Calling it again with the
same tag from the same pack updates the existing element instead of creating
a duplicate. A tag `"myLabel"` in `ui/` and a tag `"myLabel"` in `gameplay/`
are two different elements. Call it from an `"Update"` tick callback if you
want it to animate every frame.

```js
// guiWord(word, x, y, z, sizePixels, tag) → handle
// z is there because I got a bit carried over from all the 3d stuff — just pass 0 for now
const handle = Gui.guiWord("Hello world", 0.5, 0.9, 0, 0.02, "myLabel");

// setColor(handle, r, g, b, a)
Gui.setColor(handle, 0, 1, 0, 1); // green

// toTop(handle) / toBottom(handle) — reorder in draw list
Gui.toTop(handle);
Gui.toBottom(handle);

// removeWord(handle) — removes the element entirely
Gui.removeWord(handle);

// getHandleByTag(tag) → handle  — look up a handle you may have lost
// (only finds handles created by this same pack)
const h = Gui.getHandleByTag("myLabel");
```

### Clickable GUI elements

Every GUI element is clickable. When clicked, the engine fires `tick(tpf, tag)`
for that element's tag **within the same pack that created the element** — so
a button is just a `guiWord` whose tag matches an `Engine.onTick` callback
registered in the same pack's script(s). This works independently of the
`"Update"` rule above — clicks dispatch directly to their tag, they don't need
to be named `"Update"`:

```js
const btn = Gui.guiWord("[ Place Stone ]", 0.5, 0.05, 0, 0.025, "placeStoneBtn");
Gui.setColor(btn, 0.4, 0.8, 1.0, 1.0);

Engine.onTick(function(tpf, tag) {
    Block.place(10, 5, 0, 2);
    Gui.setColor(btn, 0, 1, 0, 1); // flash green on click
}, "placeStoneBtn");
```

Click dispatch and regular per-frame ticking share the same callback type —
a callback tagged `"Update"` runs every frame *and* would run again if a GUI
element in the same pack also happened to be tagged `"Update"` and got
clicked.

</details>

<!-- NEW: Matrix section documenting updated API -->

<details>
<summary><strong>Matrix — register custom blocks & per-block key/value storage</strong></summary>

`Matrix` is the pack-scoped registry and simple key/value store exposed to
mods for two purposes:

1. Registering new block types at runtime (mod-provided blocks). These get
   negative block IDs and are persisted in `worlds/<world>/registered-blocks.xml`.
2. Storing arbitrary per-block coordinate data (see `BlockValuesStore` usage in Java).

The API has been extended to allow mods to pass explicit material colours and
shininess when registering a new block. The old `addNew(name, builderType, propertiesJson)`
form still exists for backwards compatibility and delegates to the new form
with sensible defaults.

JS-visible signatures (available as `Matrix` global):

- `Matrix.addNew(name, builderType, propertiesJson) -> int`
  - Backwards-compatible form. Registers a block with default colours
    (opaque white diffuse, black specular, shininess 0) and returns the
    negative dynamic block ID.

- `Matrix.addNew(name, builderType, propertiesJson,
                 dr, dg, db, da,
                 sr, sg, sb, sa,
                 shininess) -> int`
  - New overload that accepts diffuse RGBA (dr..da), specular RGBA (sr..sa)
    and a shininess float. All numeric arguments are 0..1 for colours and a
    non-negative float for shininess. Example values are floats (JS Number).
  - Example:
    ```js
    const id = Matrix.addNew("RedBlock", "Cube", "{}",
                             0.8, 0.1, 0.1, 1.0,   // diffuse r,g,b,a
                             0.3, 0.3, 0.3, 1.0,   // specular r,g,b,a
                             12.0);                // shininess
    ```

- `Matrix.getId(name, modPackName) -> Integer|null`
  - Returns the numeric block ID for a named dynamic block in the specified
    mod pack. If `modPackName` is null or empty the Matrix instance's own
    pack name is used. Returns `null` when not found or when the dynamic
    registry isn't initialized.
  - Example:
    ```js
    const id = Matrix.getId("RedBlock", "MyPack");
    if (id !== null) {
      // use id
    }
    ```

- `Matrix.getProperties(name, modPackName) -> string`
  - Returns the raw properties JSON string that was stored during registration
    (same behaviour as before).

Notes & compatibility
- Existing mods that call the old `addNew(name,builder,props)` will continue
  to work unchanged; their blocks receive default colours. New mods can call
  the extended overload to opt into custom colours.
- Registered blocks (with colours) are persisted so colours survive restarts.
- Chunk rendering now reads colours from the dynamic registry for mod blocks
  so specifying colours will visibly affect materials in the world.

</details>
