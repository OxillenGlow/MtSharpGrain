// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "rover",
  locomotion: "ground",
  speed: 1.2,
  rarity: 6,
  visibility: 28,
  size: 0.9,
  onBuild(ctx) {
    const P = NpcPack.part;
    const chassis = P(ctx.main, ctx.name, "chassis", 0, 0, 0, 0.9, 0.3, 0.6, 0.3, 0.8, 0.3);
    const w1 = P(ctx.main, ctx.name, "w1", 0.5, -0.25, 0.35, 0.35, 0.35, 0.15, 0.1, 0.1, 0.1);
    const w2 = P(ctx.main, ctx.name, "w2", -0.5, -0.25, 0.35, 0.35, 0.35, 0.15, 0.1, 0.1, 0.1);
    const head = P(ctx.main, ctx.name, "head", 0, 0.35, 0, 0.4, 0.3, 0.4, 0.9, 0.9, 0.2);
    const light = NpcPack.light(ctx.main, ctx.name, "lamp", 0.8, 0.9, 0.3, 5);
    return { parts: { chassis, w1, w2, head, light }, extra: { roll: 0 } };
  },
  onTick(state, ctx) {
    state.extra.roll += ctx.tpf * state.speed;
    Scene.setRotation(state.parts.w1, state.extra.roll, 0, 0);
    Scene.setRotation(state.parts.w2, state.extra.roll, 0, 0);
  },
  onClick() { return "You tapped a rover — it doesn't react."; }
});
