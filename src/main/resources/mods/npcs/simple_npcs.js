(function () {
  const MOD = "simple_npcs";
  const pack = globalThis.NpcPack || (globalThis.NpcPack = { queue: [] });
  const TYPES = {};
  const npcs = {};
  const partToMain = {};
  const gridOcc = {};
  const GRID = 10;
  let nextId = 1, kills = 0, guiMsg = null, guiExpire = 0, statsHandle = null;

  try {
    const s = Data.get("kills_v1");
    if (s) kills = parseInt(JSON.parse(s).kills || 0, 10) || 0;
  } catch (e) { kills = 0; }

  function saveKills() { Data.save(JSON.stringify({ kills }), "kills_v1"); }
  function gridKey(x, z) { return Math.floor(x / GRID) + "," + Math.floor(z / GRID); }
  function showGui(msg) {
    if (guiMsg) Gui.removeWord(guiMsg);
    guiMsg = Gui.guiWord(msg, 0.5, 0.9, 0, 20, "npc_hit_msg");
    Gui.setColor(guiMsg, 1, 1, 1, 1);
    Gui.toTop(guiMsg);
    guiExpire = Date.now() + 1500;
  }
  function updateStats() {
    const txt = "Kills: " + kills + " | NPCs: " + Object.keys(npcs).length;
    if (statsHandle) Gui.removeWord(statsHandle);
    statsHandle = Gui.guiWord(txt, 0.02, 0.98, 0, 14, "npc_stats");
    Gui.setColor(statsHandle, 1, 1, 0.6, 1);
  }
  function surfaceY(x, z, hint) {
    const top = (typeof hint === "number") ? Math.floor(hint) + 24 : 200;
    for (let y = top; y >= 1; y--) if (Block.get(Math.floor(x), y, Math.floor(z)) !== 0) return y + 1;
    return null;
  }
  function airAt(x, y, z) { return Block.get(Math.floor(x), Math.floor(y), Math.floor(z)) === 0; }

  // Called from type files. Safe if this file loads before or after them.
  function register(def) {
    if (!def || !def.name) return;
    TYPES[def.name] = def;
  }
  pack.register = register;
  pack.TYPES = TYPES;
  for (const d of pack.queue) register(d);
  pack.queue.length = 0;

  // Helpers type files call inside onBuild / onTick (not at load time).
  pack.part = function (main, idName, suffix, ox, oy, oz, sx, sy, sz, r, g, b) {
    const n = idName + ":" + suffix;
    const h = Scene.createRectangle(n, sx, sy, sz);
    Scene.setColor(h, r, g, b, 1);
    Scene.attachChild(main, h);
    Scene.setRelativePosition(h, ox, oy, oz);
    partToMain[h] = idName;
    partToMain[n] = idName;
    return h;
  };
  pack.light = function (main, idName, suffix, r, g, b, radius) {
    const n = idName + ":" + suffix;
    const h = Scene.createLight(n, r, g, b, radius);
    Scene.attachChild(main, h);
    Scene.setRelativePosition(h, 0, 0.2, 0);
    partToMain[n] = idName;
    return h;
  };

  function weightOf(def) {
    const loco = def.locomotion || "ground";
    const bias = loco === "fly" ? 0.65 : loco === "interact" ? 0.8 : 1;
    return Math.max(0.01, (def.rarity || 1) * bias);
  }

  function pickType() {
    let names = Object.keys(TYPES);
    if (!names.length) return null;
    let sum = 0;
    for (const n of names) sum += weightOf(TYPES[n]);
    let r = Math.random() * sum;
    for (const n of names) {
      r -= weightOf(TYPES[n]);
      if (r <= 0) return TYPES[n];
    }
    return TYPES[names[names.length - 1]];
  }

  function destroyNPC(idName, countKill) {
    const info = npcs[idName];
    if (!info) return;
    try { Scene.destroy(info.main); } catch (e) {}
    const gk = gridKey(info.px, info.pz);
    if (gridOcc[gk] === idName) delete gridOcc[gk];
    delete npcs[idName];
    if (countKill) { kills++; saveKills(); }
    updateStats();
  }

  function rebuild(info) {
    const def = TYPES[info.type];
    if (!def) return;
    const built = def.onBuild({ x: info.px, y: info.py, z: info.pz, main: info.main, name: info.mainName }) || {};
    info.parts = built.parts || {};
    info.extra = built.extra || {};
    info.speed = def.speed || 1;
    info.destroy = false;
    info.become = null;
  }

  function spawn(def, x, y, z) {
    const id = nextId++;
    const mainName = MOD + "#" + def.name + "#" + id;
    const main = Scene.createNode(mainName);
    Scene.setPosition(main, x, y, z);
    Scene.attachChild(0, main);
    partToMain[main] = mainName;
    partToMain[mainName] = mainName;
    const info = {
      id, type: def.name, mainName, main,
      parts: {}, extra: {},
      px: x, py: y, pz: z, tx: x, ty: y, tz: z,
      speed: def.speed || 1, lastTarget: 0, born: Date.now(),
      destroy: false, become: null
    };
    npcs[mainName] = info;
    gridOcc[gridKey(x, z)] = mainName;
    rebuild(info);
    updateStats();
    return info;
  }

  function sameOf(type, except) {
    const out = [];
    for (const k in npcs) if (npcs[k].type === type && k !== except) out.push(npcs[k]);
    return out;
  }

  function ctxFor(info, tpf, player) {
    return {
      x: info.px, y: info.py, z: info.pz, main: info.main, name: info.mainName,
      tpf: tpf || 0, player,
      same: sameOf(info.type, info.mainName),
      block: (x, y, z) => Block.get(Math.floor(x), Math.floor(y), Math.floor(z)),
      surfaceY: (x, z) => surfaceY(x, z, info.py),
      airAt
    };
  }

  function trySpawn() {
    if (!Object.keys(TYPES).length) return;
    const p = Player.getPosition();
    let made = 0;
    for (let i = 0; i < 24 && made < 2; i++) {
      const def = pickType();
      if (!def) return;
      const rad = def.visibility || 30;
      const ang = Math.random() * Math.PI * 2;
      const r = Math.sqrt(Math.random()) * Math.min(rad, 40);
      const x = Math.floor(p[0] + Math.cos(ang) * r);
      const z = Math.floor(p[2] + Math.sin(ang) * r);
      const gk = gridKey(x, z);
      if (gridOcc[gk]) continue;
      const sy = surfaceY(x, z, p[1]);
      if (sy == null) continue;
      let y = sy;
      if ((def.locomotion || "ground") === "fly") {
        y = sy + 2 + Math.floor(Math.random() * 5);
        if (!airAt(x, y, z)) continue;
      } else if (!airAt(x, sy, z) || Block.get(x, sy - 1, z) === 0) continue;
      spawn(def, x + 0.5, y, z + 0.5);
      made++;
    }
  }

  function retarget(info) {
    const def = TYPES[info.type];
    const loco = (def && def.locomotion) || "ground";
    const reach = loco === "fly" ? 8 : loco === "blob" ? 4 : 6;
    for (let i = 0; i < 8; i++) {
      const tx = Math.floor(info.px + (Math.random() * 2 - 1) * reach);
      const tz = Math.floor(info.pz + (Math.random() * 2 - 1) * reach);
      const gk = gridKey(tx, tz);
      if (gridOcc[gk] && gridOcc[gk] !== info.mainName) continue;
      if (loco === "fly") {
        const base = surfaceY(tx, tz, info.py);
        const ty = (base == null ? info.py : base + 2) + Math.floor(Math.random() * 5);
        if (!airAt(tx, ty, tz)) continue;
        info.tx = tx + 0.5; info.ty = ty; info.tz = tz + 0.5;
      } else {
        const sy = surfaceY(tx, tz, info.py + 2);
        if (sy == null) continue;
        if (!airAt(tx, sy, tz)) continue;
        // stairs: accept a rise of up to 2 blocks
        if (sy > info.py + 2.2) continue;
        info.tx = tx + 0.5; info.ty = sy; info.tz = tz + 0.5;
      }
      info.lastTarget = Date.now();
      return;
    }
    info.tx = info.px; info.ty = info.py; info.tz = info.pz;
    info.lastTarget = Date.now();
  }

  // One-block step-up while walking so a slope of stairs is climbable.
  function groundStep(info, nx, ny, nz) {
    const ix = Math.floor(nx), iz = Math.floor(nz);
    const feet = Math.floor(ny);
    if (airAt(ix, feet, iz) && Block.get(ix, feet - 1, iz) !== 0) return ny;
    if (!airAt(ix, feet, iz) && airAt(ix, feet + 1, iz)) return feet + 1;
    return ny;
  }

  let last = Date.now();
  Engine.setInterval(function () {
    try { trySpawn(); } catch (e) { console.error("spawn", e); }
  }, 5000);

  Engine.setInterval(function () {
    const now = Date.now();
    const tpf = Math.min(0.05, (now - last) / 1000);
    last = now;
    const player = Player.getPosition();
    const dead = [];
    for (const idName in npcs) {
      const info = npcs[idName];
      const def = TYPES[info.type];
      if (!def) { dead.push(idName); continue; }
      const dx = info.px - player[0], dy = info.py - player[1], dz = info.pz - player[2];
      const vis = def.visibility || 40;
      if (dx * dx + dy * dy + dz * dz > (vis + 70) * (vis + 70)) { dead.push(idName); continue; }

      if (info.become && TYPES[info.become]) {
        try { Scene.destroy(info.main); } catch (e) {}
        info.main = Scene.createNode(info.mainName);
        Scene.attachChild(0, info.main);
        info.type = info.become;
        info.become = null;
        rebuild(info);
        Scene.setPosition(info.main, info.px, info.py, info.pz);
      }

      const distT = Math.hypot(info.tx - info.px, info.ty - info.py, info.tz - info.pz);
      if (!info.lastTarget || now - info.lastTarget > 5000 || distT < 0.45) retarget(info);

      const dirx = info.tx - info.px, diry = info.ty - info.py, dirz = info.tz - info.pz;
      const dist = Math.hypot(dirx, diry, dirz);
      if (dist > 0.001) {
        const step = Math.min((info.speed || 1) * tpf, dist);
        let nx = info.px + (dirx / dist) * step;
        let ny = info.py + (diry / dist) * step;
        let nz = info.pz + (dirz / dist) * step;
        if ((def.locomotion || "ground") !== "fly") ny = groundStep(info, nx, ny, nz);
        const og = gridKey(info.px, info.pz), ng = gridKey(nx, nz);
        if (og !== ng) {
          if (gridOcc[og] === idName) delete gridOcc[og];
          if (!gridOcc[ng]) gridOcc[ng] = idName;
        }
        info.px = nx; info.py = ny; info.pz = nz;
        Scene.setPosition(info.main, nx, ny, nz);
      }

      try {
        if (def.onTick) def.onTick(info, ctxFor(info, tpf, player));
      } catch (e) { console.error(info.type, e); }
      if (info.destroy) dead.push(idName);
    }
    for (const id of dead) destroyNPC(id, false);
    if (guiMsg && now > guiExpire) { Gui.removeWord(guiMsg); guiMsg = null; }
  }, 16);

  Engine.onSpatialLeftClick(function (spatialName) {
    const idName = partToMain[spatialName] || spatialName;
    const info = npcs[idName];
    if (!info) return;
    const def = TYPES[info.type];
    if (!def || !def.onClick) return;
    const msg = def.onClick(info, ctxFor(info, 0, Player.getPosition()));
    if (typeof msg === "string") showGui(msg + "  Total kills: " + kills);
    if (info.destroy) destroyNPC(idName, true);
  });

  Engine.onTick(function (tpf, tag) {
    if (tag === "npc_stats") showGui("Kills: " + kills + " | NPCs: " + Object.keys(npcs).length + " | types: " + Object.keys(TYPES).length);
  }, "npc_stats");

  updateStats();
})();
