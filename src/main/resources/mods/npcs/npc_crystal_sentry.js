// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "crystal_sentry",
  locomotion: "ground",
  speed: 0.4,
  rarity: 3,
  visibility: 28,
  size: 0.6,
  onBuild(ctx) {
    const P = NpcPack.part;
    const base = P(ctx.main, ctx.name, "base", 0, 0, 0, 0.6, 0.25, 0.6, 0.25, 0.3, 0.45);
    const shard = P(ctx.main, ctx.name, "shard", 0, 0.45, 0, 0.22, 0.7, 0.22, 0.4, 0.8, 1);
    const light = NpcPack.light(ctx.main, ctx.name, "glow", 0.35, 0.7, 1, 6);
    return { parts: { base, shard, light }, extra: { t: 0 } };
  },
  onTick(state, ctx) {
    state.extra.t += ctx.tpf;
    Scene.setRotation(state.parts.shard, 0, state.extra.t, 0);
    Scene.setLightColor(state.parts.light, 0.3, 0.55 + Math.sin(state.extra.t * 2) * 0.3, 1);
    // interact: if the player is close, stop wandering and face them
    const dx = ctx.player[0] - state.px, dz = ctx.player[2] - state.pz;
    if (dx * dx + dz * dz < 6 * 6) {
      state.tx = state.px; state.tz = state.pz;
      Scene.setRotation(state.main, 0, Math.atan2(dx, dz), 0);
    }
  },
  onClick() { return "The sentry hums and ignores you."; }
});
