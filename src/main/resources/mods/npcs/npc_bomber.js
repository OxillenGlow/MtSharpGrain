// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "bomber",
  locomotion: "fly",
  speed: 2.4,
  rarity: 2,
  visibility: 34,
  size: 0.9,
  onBuild(ctx) {
    const P = NpcPack.part;
    const body = P(ctx.main, ctx.name, "body", 0, 0, 0, 0.9, 0.35, 0.7, 0.6, 0.1, 0.6);
    const r1 = P(ctx.main, ctx.name, "r1", 0.7, 0.25, 0, 0.35, 0.05, 0.35, 0.15, 0.15, 0.15);
    const r2 = P(ctx.main, ctx.name, "r2", -0.7, 0.25, 0, 0.35, 0.05, 0.35, 0.15, 0.15, 0.15);
    const light = NpcPack.light(ctx.main, ctx.name, "core", 0.7, 0.15, 0.9, 5);
    return { parts: { body, r1, r2, light }, extra: { pulse: 0 } };
  },
  onTick(state, ctx) {
    state.extra.pulse += ctx.tpf;
    const s = 4 + Math.sin(state.extra.pulse * 3) * 1.5;
    Scene.setLightRadius(state.parts.light, s);
  },
  onClick(state) {
    state.become = "drone";
    return "You hit a bomber — it degraded!";
  }
});
