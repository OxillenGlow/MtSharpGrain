// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "blob",
  locomotion: "ground",
  speed: 0.6,
  rarity: 5,
  visibility: 24,
  size: 0.7,
  onBuild(ctx) {
    const P = NpcPack.part;
    const mass = P(ctx.main, ctx.name, "mass", 0, 0, 0, 0.7, 0.5, 0.7, 0.2, 0.9, 0.5);
    const eye = P(ctx.main, ctx.name, "eye", 0, 0.25, 0.25, 0.15, 0.15, 0.15, 0, 0, 0);
    const light = NpcPack.light(ctx.main, ctx.name, "glow", 0.2, 1, 0.5, 3.5);
    return { parts: { mass, eye, light }, extra: { bob: Math.random() * 6 } };
  },
  onTick(state, ctx) {
    state.extra.bob += ctx.tpf * 3;
    Scene.setRelativePosition(state.parts.mass, 0, Math.sin(state.extra.bob) * 0.05, 0);
    Scene.setLightRadius(state.parts.light, 3 + Math.sin(state.extra.bob) * 0.6);
  },
  onClick() { return "You touched a blob — nothing happened."; }
});
