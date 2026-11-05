// Drop this file in the same mod pack folder as simple_npcs.js.
// It only registers a type. The manager spawns, moves, and destroys.
(function (def) {
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  if (pack.register) pack.register(def);
  else pack.queue.push(def);
})({
  name: "stair_beetle",
  locomotion: "ground",
  speed: 1.5,
  rarity: 6,
  visibility: 26,
  size: 0.55,
  onBuild(ctx) {
    const P = NpcPack.part;
    const shell = P(ctx.main, ctx.name, "shell", 0, 0.1, 0, 0.55, 0.28, 0.7, 0.45, 0.28, 0.12);
    const head = P(ctx.main, ctx.name, "head", 0, 0.05, 0.4, 0.28, 0.2, 0.22, 0.2, 0.15, 0.1);
    const light = NpcPack.light(ctx.main, ctx.name, "lamp", 1, 0.55, 0.15, 3.5);
    return { parts: { shell, head, light }, extra: {} };
  },
  onTick(state, ctx) {
    // face travel direction; manager already steps the body up stairs
    const dx = state.tx - state.px, dz = state.tz - state.pz;
    if (dx * dx + dz * dz > 0.05) Scene.setRotation(state.main, 0, Math.atan2(dx, dz), 0);
  },
  onClick() { return "The beetle clicks its shell and keeps climbing."; }
});
