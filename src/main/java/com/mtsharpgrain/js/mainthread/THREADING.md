# Mod threading model (background vs MAIN)

This document explains how JavaScript mod packs are scheduled and what the
`MAIN` suffix means. Read it before writing performance-sensitive or
render-thread mods.

## Two pack kinds

| Kind | Folder name rule | Thread | Tick path | EngineAccess |
|------|------------------|--------|-----------|--------------|
| **Background** (default) | does **not** end with `MAIN` | dedicated virtual thread + `ModBridge` mailbox | `submitTick` / tagged `"Tick"` + optional `update(tpf)` | enqueue + wait (suspends only the virtual thread) |
| **MAIN** | name **ends with** `MAIN` (case-sensitive), e.g. `hudMAIN`, `physicsMAIN` | **render / main thread** | **only** `"Update"` tag + optional top-level `update(tpf)` | **direct** (no `app.enqueue`) |

Always-on GUI prefixes (`LFT`, `RHT`, `BTM`, `MODE`, `UTIL`) are independent of
`MAIN`. You can combine them, e.g. `LFThudMAIN`.

## Why MAIN exists

Background packs keep heavy or blocking JS off the frame loop. Sometimes you
need the opposite: zero-latency, same-frame reaction to world state with no
queue hop. MAIN packs run on the render thread for that case.

**Trade-off:** a slow or blocking MAIN pack stalls the entire game frame.
Prefer background packs unless you have measured a real need for main-thread
execution.

## Shared machinery

Both kinds share:

- One GraalJS `Context` per pack (globals and handles stay isolated).
- The same JS API surface (`Scene`, `Block`, `Engine`, `Gui`, `Player`, `Mod`, …).
- Inter-mod messaging (`Mod.send` / `onReceive`) via `ModBridge`.
- Block validation, spatial clicks, block-loader events, GUI draw state.

`ModPackManager` keeps two parallel maps (`backgroundPacks` and `mainPacks`)
plus a unified `packs` map for lookups and broadcast.

## Best practices for MAIN packs

1. **Only use the `"Update"` tag**  
   ```js
   Engine.onTick(function(tpf, tag) {
     // tag is always "Update" for MAIN packs driven by the manager
   }, "Update");
   ```
   Or define a top-level:
   ```js
   function update(tpf) { /* per-frame */ }
   ```
   Other tags are not driven by the manager for MAIN packs.

2. **Do not use `Engine.setInterval` / `setTimeout`**  
   Timers still queue work onto the pack bridge. On MAIN packs that work is
   drained on the next frame, but the timer scheduler is a separate thread and
   is easy to misuse. Prefer pure Update-driven logic or a background pack.

3. **Keep Update work tiny**  
   A few matrix math ops or a short state machine is fine. Nested loops over
   the whole world, network I/O, or long JSON parsing are not.

4. **Do not block**  
   No busy-waits, no synchronous file reads of large data, no infinite loops.

5. **Messaging is still async across kinds**  
   `Mod.send` from a MAIN pack to a background pack (and the reverse) goes
   through the recipient’s bridge. Do not assume same-frame delivery to
   background packs.

6. **Handles stay per-pack**  
   Scene / Gui handles created in a MAIN pack are invalid in any other pack,
   including other MAIN packs. Cross-pack data still goes through `Mod` or
   shared world state (`Block`, `Matrix` where applicable).

## Best practices for background packs

- Prefer them for anything non-trivial.
- Use tagged ticks (`"Tick"` or your own tags) for periodic work.
- `EngineAccess` already protects the render thread; a blocked virtual thread
  does not freeze the game.

## Lifecycle notes

- MAIN packs are loaded **synchronously** during `loadAll` (called from
  `simpleInitApp` on the render thread).
- Background packs still start on virtual threads and run a mailbox loop until
  shutdown.
- Disable / reload works for both; MAIN reloads are posted back to the render
  thread when necessary.

## Quick checklist

- [ ] Need same-frame, zero-enqueue access to jME state? → suffix `MAIN`.
- [ ] Heavy logic, timers, AI, pathfinding? → normal (background) pack.
- [ ] MAIN pack registers only `"Update"` (or uses `update(tpf)`).
- [ ] MAIN pack does not call `setInterval` / long work in Update.
- [ ] Cross-pack talk still uses `Mod.send` / `onReceive`.
