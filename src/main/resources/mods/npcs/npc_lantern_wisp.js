// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "lantern_wisp",
  locomotion: "fly",
  speed: 1.6,
  rarity: 4,
  visibility: 36,
  size: 0.35,
  onBuild(ctx) {
    const core = NpcPack.part(ctx.main, ctx.name, "core", 0, 0, 0, 0.28, 0.28, 0.28, 1, 0.85, 0.4);
    const light = NpcPack.light(ctx.main, ctx.name, "glow", 1, 0.75, 0.3, 7);
    return { parts: { core, light }, extra: { t: Math.random() * 10 } };
  },
  onTick(state, ctx) {
    state.extra.t += ctx.tpf;
    // drift vertically inside the air column the manager already picked
    state.ty += Math.sin(state.extra.t) * ctx.tpf * 0.4;
    Scene.setLightRadius(state.parts.light, 6 + Math.sin(state.extra.t * 4) * 1.2);
  },
  onClick(state) {
    state.destroy = true;
    return "The wisp pops.";
  }
});
