// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "miner_bot",
  locomotion: "interact",
  speed: 1.0,
  rarity: 3,
  visibility: 26,
  size: 0.7,
  onBuild(ctx) {
    const P = NpcPack.part;
    const body = P(ctx.main, ctx.name, "body", 0, 0.2, 0, 0.55, 0.4, 0.55, 0.35, 0.4, 0.45);
    const arm = P(ctx.main, ctx.name, "arm", 0.4, 0.15, 0.2, 0.15, 0.15, 0.4, 0.7, 0.7, 0.2);
    const light = NpcPack.light(ctx.main, ctx.name, "lamp", 1, 0.85, 0.4, 5);
    return { parts: { body, arm, light }, extra: { found: 0 } };
  },
  onTick(state, ctx) {
    // sniff crystal ore (id 5) in a small column underfoot; flash if found
    const x = Math.floor(state.px), z = Math.floor(state.pz);
    let hit = false;
    for (let dy = 0; dy < 4; dy++) {
      if (ctx.block(x, state.py - 1 - dy, z) === 5) { hit = true; break; }
    }
    state.extra.found = hit ? 1 : 0;
    Scene.setLightColor(state.parts.light, hit ? 0.2 : 1, hit ? 1 : 0.85, hit ? 0.3 : 0.4);
    Scene.setLightRadius(state.parts.light, hit ? 8 : 4);
    if (hit) { state.tx = state.px; state.tz = state.pz; }
  },
  onClick(state) {
    return state.extra.found ? "Miner found crystal underfoot." : "Miner is still searching.";
  }
});
