// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "shadow_skitter",
  locomotion: "ground",
  speed: 2.2,
  rarity: 4,
  visibility: 22,
  size: 0.4,
  onBuild(ctx) {
    const body = NpcPack.part(ctx.main, ctx.name, "body", 0, 0, 0, 0.45, 0.18, 0.45, 0.08, 0.08, 0.1);
    return { parts: { body }, extra: {} };
  },
  onTick(state, ctx) {
    // flee the player; no light on purpose
    const dx = state.px - ctx.player[0], dz = state.pz - ctx.player[2];
    const d2 = dx * dx + dz * dz;
    if (d2 < 8 * 8 && d2 > 0.01) {
      const d = Math.sqrt(d2);
      state.tx = state.px + (dx / d) * 5;
      state.tz = state.pz + (dz / d) * 5;
    }
  },
  onClick(state) {
    state.destroy = true;
    return "The skitter vanishes.";
  }
});
