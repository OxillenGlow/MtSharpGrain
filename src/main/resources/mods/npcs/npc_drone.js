// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "drone",
  locomotion: "fly",
  speed: 3.0,
  rarity: 10,
  visibility: 32,
  size: 0.8,
  onBuild(ctx) {
    const P = NpcPack.part;
    const body = P(ctx.main, ctx.name, "body", 0, 0, 0, 0.8, 0.2, 0.6, 0.4, 0.4, 0.9);
    const r1 = P(ctx.main, ctx.name, "r1", 0.6, 0.2, 0, 0.3, 0.05, 0.3, 0.2, 0.2, 0.2);
    const r2 = P(ctx.main, ctx.name, "r2", -0.6, 0.2, 0, 0.3, 0.05, 0.3, 0.2, 0.2, 0.2);
    const r3 = P(ctx.main, ctx.name, "r3", 0, 0.2, 0.6, 0.3, 0.05, 0.3, 0.2, 0.2, 0.2);
    const r4 = P(ctx.main, ctx.name, "r4", 0, 0.2, -0.6, 0.3, 0.05, 0.3, 0.2, 0.2, 0.2);
    return { parts: { body, r1, r2, r3, r4 }, extra: { spin: 0 } };
  },
  onTick(state, ctx) {
    state.extra.spin += ctx.tpf * 12;
    Scene.setRotation(state.main, 0, state.extra.spin, 0);
  },
  onClick(state) {
    state.destroy = true;
    return "You hit a drone — killed!";
  }
});
