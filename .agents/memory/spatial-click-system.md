---
name: Spatial click system
description: How chunk vs non-chunk raycasting works and how spatial-click events reach JS mods.
---

## Rule
Chunk detection is purely name-based — no userdata. Chunk spatials are always named `Ck<X>y<Y>z<Z>` (e.g. `Ck0y0z0`, `Ck-1y2z-3`). The regex `Ck-?\d+y-?\d+z-?\d+` is the single source of truth, defined in `BlockSelector`.

**Why:** The user explicitly asked not to use userdata. The chunk builder (`ChunkMeshBuilder`) sets this name on the outermost batched spatial and it never changes, so name matching is reliable.

**How to apply:** Any time you need to distinguish chunk geometry from mod-created spatials, use `BlockSelector.isChunkHit(CollisionResult)`. Do not add userdata flags as an alternative — keep the name-only contract.

## Architecture

- `BlockSelector.raycast()` → raw `CollisionResult`
- `BlockSelector.isChunkHit(hit)` → walks up parent chain checking the regex
- `BlockSelector.resolveHitName(hit)` → walks up and returns first non-chunk, non-empty name (the mod-given name)
- `BlockSelector.selectionFrom(hit, leftPressed)` → computes block coords (only call after confirming chunk hit)
- `Check.onAction` → dispatches to block logic OR `modPackManager.notifySpatialLeftClick/RightClick(name)`
- `Check.setModPackManager(mpm)` must be called after mods load (mods load after Check in Main.java)

## JS API (mods)
```js
Engine.onSpatialLeftClick(function(name) { /* name = spatial's creation-time name */ });
Engine.onSpatialRightClick(function(name) { /* same */ });
```
Backed by `SpatialClickRegistry` (new class in `com.mtsharpgrain.js.mainthread`), wired via `JsApiBootstrap`. Uses `@HostAccess.Export` on `onLeftClick`/`onRightClick` — required because the context uses `HostAccess.EXPLICIT`.
