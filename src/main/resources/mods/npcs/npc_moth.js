// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "moth",
  locomotion: "fly",
  speed: 2.8,
  rarity: 4,
  visibility: 30,
  size: 0.5,
  onBuild(ctx) {
    const P = NpcPack.part;
    const body = P(ctx.main, ctx.name, "body", 0, 0, 0, 0.2, 0.15, 0.45, 0.85, 0.8, 0.7);
    const wingL = P(ctx.main, ctx.name, "wl", -0.35, 0.05, 0, 0.45, 0.02, 0.3, 0.95, 0.9, 0.6);
    const wingR = P(ctx.main, ctx.name, "wr", 0.35, 0.05, 0, 0.45, 0.02, 0.3, 0.95, 0.9, 0.6);
    const light = NpcPack.light(ctx.main, ctx.name, "dust", 1, 0.95, 0.7, 3);
    return { parts: { body, wingL, wingR, light }, extra: { flap: 0 } };
  },
  onTick(state, ctx) {
    state.extra.flap += ctx.tpf * 18;
    const a = Math.sin(state.extra.flap) * 0.6;
    Scene.setRotation(state.parts.wingL, 0, 0, a);
    Scene.setRotation(state.parts.wingR, 0, 0, -a);
    const dx = ctx.player[0] - state.px, dz = ctx.player[2] - state.pz;
    const d2 = dx * dx + dz * dz;
    if (d2 < 10 * 10 && d2 > 4) {
      state.tx = ctx.player[0] + Math.sin(state.extra.flap) * 2;
      state.tz = ctx.player[2] + Math.cos(state.extra.flap) * 2;
      state.ty = ctx.player[1] + 1.2;
    }
  },
  onClick(state) {
    state.destroy = true;
    return "The moth scatters.";
  }
});
