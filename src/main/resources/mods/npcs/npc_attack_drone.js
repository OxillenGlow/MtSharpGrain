// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "attack_drone",
  locomotion: "fly",
  speed: 3.2,
  rarity: 3,
  visibility: 34,
  size: 0.8,
  onBuild(ctx) {
    const P = NpcPack.part;
    const body = P(ctx.main, ctx.name, "body", 0, 0, 0, 0.8, 0.2, 0.6, 0.8, 0.3, 0.3);
    const r1 = P(ctx.main, ctx.name, "r1", 0.6, 0.2, 0, 0.3, 0.05, 0.3, 0.2, 0.2, 0.2);
    const r2 = P(ctx.main, ctx.name, "r2", -0.6, 0.2, 0, 0.3, 0.05, 0.3, 0.2, 0.2, 0.2);
    const light = NpcPack.light(ctx.main, ctx.name, "eye", 1, 0.25, 0.2, 4);
    return { parts: { body, r1, r2, light }, extra: {} };
  },
  onTick(state, ctx) {
    // lean toward the player when they are close, still flying
    const dx = ctx.player[0] - state.px, dz = ctx.player[2] - state.pz;
    if (dx * dx + dz * dz < 12 * 12) {
      state.tx = ctx.player[0];
      state.tz = ctx.player[2];
      state.ty = ctx.player[1] + 1.5;
    }
  },
  onClick(state) {
    state.become = "drone";
    return "You hit an attack drone — it degraded!";
  }
});
