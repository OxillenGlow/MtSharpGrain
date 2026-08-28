(function(){
  const MOD = "simple_npcs";
  let npcs = {}, partToMain = {}, chunkCounts = {}, nextId = 1;
  let kills = 0, accSpawn = 0, guiMsg = null, guiExpire = 0;
  // occupancy grid per 10x10 area so we don't spawn more than one per 10x10
  const gridSize = 10;
  const gridOccupancy = {}; // key = gx+","+gz -> mainName

  // load saved kills
  try{ const s = Data.get("kills_v1"); if(s) kills = parseInt(JSON.parse(s).kills||0,10) || 0; }catch(e){ kills = 0; }

  function saveKills(){ Data.save(JSON.stringify({kills:kills}), "kills_v1"); }
  function chunkOf(x,y,z){ let cx=Math.floor(x/32), cy=Math.floor(y/32), cz=Math.floor(z/32); return {cx,cy,cz,key:cx+","+cy+","+cz}; }
  function incChunk(key,d){ chunkCounts[key]=(chunkCounts[key]||0)+d; if(chunkCounts[key]<=0) delete chunkCounts[key]; }
  function rndFloat(a,b){ return a + Math.random()*(b-a); }
  function int(v){ return Math.floor(v); }
  function showGui(msg){ if(guiMsg) Gui.removeWord(guiMsg); guiMsg = Gui.guiWord(msg,0.5,0.9,0,20,"npc_hit_msg"); Gui.setColor(guiMsg,1,1,1,1); Gui.toTop(guiMsg); guiExpire = Date.now()+1500; }

  // grid helpers (10x10 surface occupancy)
  function gridKeyFor(x,z){ const gx = Math.floor(x / gridSize); const gz = Math.floor(z / gridSize); return gx+","+gz; }

  // create rectangle helper (attach child and map handles to main)
  function mkRectAttached(parentHandle, mainName, suffix, ox, oy, oz, size, color, parts){
    const pname = mainName + ":" + suffix;
    const p = Scene.createRectangle(pname, size[0], size[1], size[2]);
    Scene.setColor(p, color[0], color[1], color[2], 1);
    Scene.attachChild(parentHandle, p);
    Scene.setRelativePosition(p, ox, oy, oz);
    parts.push(p);
    partToMain[p] = mainName;
    partToMain[pname] = mainName;
  }
  function mainNameHandle(mainName){
    if(npcs[mainName] && npcs[mainName].main) return npcs[mainName].main;
    return Scene.createNode(mainName);
  }

  // better surface finder using explicit z param
  function surfaceYAt(x,z, maxY){
    maxY = (typeof maxY === 'number') ? maxY : 200;
    for(let y = maxY; y>=1; y--){ if(Block.get(x,y,z) !== 0) return y+1; }
    return null;
  }

  function createNPCOfType(type,x,y,z){
    const id = nextId++;
    const mainName = MOD + "_npc#" + id;
    const main = Scene.createNode(mainName);
    Scene.setPosition(main, x, y, z);
    Scene.attachChild(0, main);
    let parts = [];
    partToMain[main] = mainName; partToMain[mainName] = mainName;

    if(type === "drone" || type === "attack_drone" || type === "bomber"){
      const cols = type==="attack_drone"?[0.8,0.3,0.3] : (type==="bomber"?[0.6,0.1,0.6] : [0.4,0.4,0.9]);
      const bodySize = [0.8, 0.2, 0.6]; const rotorSize = [0.3, 0.05, 0.3];
      mkRectAttached(main, mainName, "body", 0, 0, 0, bodySize, cols, parts);
      mkRectAttached(main, mainName, "r1", 0.6, 0.2, 0, rotorSize, [0.2,0.2,0.2], parts);
      mkRectAttached(main, mainName, "r2", -0.6, 0.2, 0, rotorSize, [0.2,0.2,0.2], parts);
      mkRectAttached(main, mainName, "r3", 0, 0.2, 0.6, rotorSize, [0.2,0.2,0.2], parts);
      mkRectAttached(main, mainName, "r4", 0, 0.2, -0.6, rotorSize, [0.2,0.2,0.2], parts);
    } else if(type === "rover"){
      const chassisSize = [0.9, 0.3, 0.6]; const wheelSize=[0.4,0.2,0.4]; const headSize=[0.4,0.3,0.4];
      mkRectAttached(main, mainName, "chassis", 0, 0, 0, chassisSize, [0.3,0.8,0.3], parts);
      mkRectAttached(main, mainName, "wheel1", 0.5, -0.25, 0.5, wheelSize, [0.1,0.1,0.1], parts);
      mkRectAttached(main, mainName, "wheel2", -0.5, -0.25, 0.5, wheelSize, [0.1,0.1,0.1], parts);
      mkRectAttached(main, mainName, "head", 0, 0.4, 0, headSize, [0.9,0.9,0.2], parts);
    } else if(type==="blob"){
      const massSize=[0.7,0.5,0.7]; const eyeSize=[0.15,0.15,0.15];
      mkRectAttached(main, mainName, "mass", 0, 0, 0, massSize, [0.2,0.9,0.5], parts);
      mkRectAttached(main, mainName, "eye", 0, 0.35, 0.2, eyeSize, [0,0,0], parts);
    } else {
      const bodySize=[0.6,0.4,0.6]; 
      const p=Scene.createRectangle(mainName+":body", bodySize[0], bodySize[1], bodySize[2]);
      Scene.setColor(p,1,1,1,1); // makes it white
      Scene.attachChild(main,p); // attaches
      Scene.setRelativePosition(p,0,0,0); // moves it to center
      parts.push(p);
      partToMain[p]=mainName; 
      partToMain[mainName+":body"]=mainName;
    }

    const ck = chunkOf(x,y,z).key;
    // store floats for smooth motion and a target for wandering
    npcs[mainName] = {id, mainName, main, parts, type, px:x, py:y, pz:z, tx:x, ty:y, tz:z, speed:1.0, lastTarget:0, chunk:ck, active:true};
    incChunk(ck,1);
    // mark grid occupancy
    const gk = gridKeyFor(x,z);
    gridOccupancy[gk] = mainName;
    updateStatsGui();
    return npcs[mainName];
  }

  function destroyNPC(mainName){
    const info = npcs[mainName]; if(!info) return;
    for(let p of info.parts){ try{ Scene.destroy(p); delete partToMain[p]; }catch(e){} }
    try{ Scene.destroy(info.main); }catch(e){}
    incChunk(info.chunk,-1);
    // clear grid occupancy
    const gk = gridKeyFor(info.px, info.pz);
    if(gridOccupancy[gk] === mainName) delete gridOccupancy[gk];
    delete npcs[mainName];
    updateStatsGui();
  }

  function degradeToDrone(info){
    for(let p of info.parts){ try{ Scene.destroy(p); delete partToMain[p]; }catch(e){} }
    info.parts = [];
    info.type = "drone";
    const pos = Scene.getPosition(info.main); const x = pos[0], y = pos[1], z = pos[2];
    const bodySize=[0.8,0.2,0.6], rotorSize=[0.3,0.05,0.3];
    mkRectAttached(info.main, info.mainName, "body", 0, 0, 0, bodySize, [0.4,0.4,0.9], info.parts);
    mkRectAttached(info.main, info.mainName, "r1", 0.6, 0.2, 0, rotorSize, [0.2,0.2,0.2], info.parts);
    mkRectAttached(info.main, info.mainName, "r2", -0.6, 0.2, 0, rotorSize, [0.2,0.2,0.2], info.parts);
    mkRectAttached(info.main, info.mainName, "r3", 0, 0.2, 0.6, rotorSize, [0.2,0.2,0.2], info.parts);
    mkRectAttached(info.main, info.mainName, "r4", 0, 0.2, -0.6, rotorSize, [0.2,0.2,0.2], info.parts);
    updateStatsGui();
  }

  Engine.onSpatialLeftClick(function(spatialName){
    const main = partToMain[spatialName] || spatialName; const info = npcs[main]; if(!info) return;
    const display = info.type.replace("_"," ");
    if(info.type === "drone"){ destroyNPC(main); kills++; saveKills(); showGui("You hit a " + display + " — killed! Total kills: " + kills); }
    else if(info.type === "attack_drone" || info.type === "bomber"){ degradeToDrone(info); showGui("You hit a " + display + " — it degraded!"); }
    else if(info.type === "rover"){ showGui("You tapped a " + display + " — it doesn't react."); }
    else if(info.type === "blob"){ showGui("You touched an " + display + " — nothing happened."); }
  });

  // spawn logic: ensure at most one per grid cell (10x10), on surface, within 30 block radius of player
  function trySpawnNearPlayer(){
    const ppos = Player.getPosition(); const px= Math.floor(ppos[0]), py=Math.floor(ppos[1]), pz=Math.floor(ppos[2]);
    const maxRadius = 30;
    const spawnAttempts = 30; // tries per spawn cycle
    let spawned = 0;
    for(let i=0;i<spawnAttempts;i++){
      // pick random offset within circle
      const ang = Math.random()*Math.PI*2;
      const r = Math.sqrt(Math.random()) * maxRadius;
      const sx = Math.floor(px + Math.cos(ang)*r);
      const sz = Math.floor(pz + Math.sin(ang)*r);
      const gk = gridKeyFor(sx,sz);
      if(gridOccupancy[gk]) continue; // already occupied in this 10x10
      // find surface
      const sy = surfaceYAt(sx, sz, py+10);
      if(sy === null) continue;
      // ensure surface block is reachable (air above)
      if(Block.get(sx, sy, sz) !== 0) continue;
      // ensure solid block below
      if(Block.get(sx, sy-1, sz) === 0) continue;
      // choose type with some bias
      const droneBias = Math.min(0.25 + kills*0.01, 0.6);
      const r2 = Math.random(); let t;
      if(r2 < 0.5 + droneBias*0.5) t = "drone";
      else if(r2 < 0.75) t = "rover";
      else if(r2 < 0.9) t = "blob";
      else if(r2 < 0.97) t = "attack_drone";
      else t = "bomber";

      createNPCOfType(t, sx + 0.5, sy, sz + 0.5); // center on block
      spawned++;
      if(spawned >= 3) break;
    }
  }

  // Smooth movement: each NPC has a target (tx,ty,tz) and speed. We lerp every frame.
  function chooseNewTarget(info){
    const pos = Scene.getPosition(info.main);
    const x = pos[0], y = pos[1], z = pos[2];
    let maxDist = 6;
    let v = 1.0;
    if(info.type === "drone" || info.type === "attack_drone" || info.type === "bomber"){ maxDist = 8; v = 3.0; }
    else if(info.type === "rover"){ maxDist = 6; v = 1.2; }
    else if(info.type === "blob"){ maxDist = 4; v = 0.6; }

    // attempt to find a valid target a few times
    for(let i=0;i<8;i++){
      const tx = Math.floor(x + rndFloat(-maxDist, maxDist));
      const tz = Math.floor(z + rndFloat(-maxDist, maxDist));
      if(info.type === "rover" || info.type === "blob"){
        const sy = surfaceYAt(tx, tz, Math.floor(y)+2);
        if(sy === null) continue;
        // ensure target cell not occupied by another npc
        const gk = gridKeyFor(tx,tz);
        if(gridOccupancy[gk] && gridOccupancy[gk] !== info.mainName) continue;
        info.tx = tx + 0.5; info.ty = sy; info.tz = tz + 0.5; info.speed = v; info.lastTarget = Date.now(); return;
      } else {
        // flying target: choose air spot above ground
        const baseY = Math.floor(y);
        const ty = Math.floor(baseY + rndFloat(-2,2));
        // check air and not colliding
        if(Block.get(tx,ty,tz) !== 0) continue;
        const gk = gridKeyFor(tx,tz);
        if(gridOccupancy[gk] && gridOccupancy[gk] !== info.mainName) continue;
        info.tx = tx + 0.5; info.ty = ty; info.tz = tz + 0.5; info.speed = v; info.lastTarget = Date.now(); return;
      }
    }
    // fallback: small random nudge
    info.tx = x + rndFloat(-1,1); info.tz = z + rndFloat(-1,1); info.ty = y; info.speed = 0.6; info.lastTarget = Date.now();
  }

  function updateMovementSmooth(tpf){
    const now = Date.now();
    const playerPos = Player.getPosition();
    const px = playerPos[0], py = playerPos[1], pz = playerPos[2];
    const disableDist = 100; // auto-disable if too far
    for(const mainName in npcs){
      const info = npcs[mainName]; if(!info || !info.active) continue;
      // auto-disable / destroy when far away from player
      const dxp = info.px - px, dzp = info.pz - pz, dyp = info.py - py;
      const dsq = dxp*dxp + dyp*dyp + dzp*dzp;
      if(dsq > disableDist*disableDist){ destroyNPC(mainName); continue; }

      // pick new target if time passed or close to current
      const distToTarget = Math.hypot(info.tx - info.px, info.ty - info.py, info.tz - info.pz);
      if(!info.lastTarget || (Date.now() - info.lastTarget) > 5000 || distToTarget < 0.5){ chooseNewTarget(info); }

      // move towards target smoothly
      const dirx = info.tx - info.px; const diry = info.ty - info.py; const dirz = info.tz - info.pz;
      const dist = Math.hypot(dirx,diry,dirz);
      if(dist > 0.001){
        const maxStep = info.speed * tpf; // units per second scaled by tpf
        const step = Math.min(maxStep, dist);
        const nx = info.px + (dirx/dist)*step;
        const ny = info.py + (diry/dist)*step;
        const nz = info.pz + (dirz/dist)*step;
        Scene.setPosition(info.main, nx, ny, nz);
        info.px = nx; info.py = ny; info.pz = nz;
      }
    }
    updateStatsGui();
  }

  let first = true;
  Engine.onTick(function(tpf,tag){
    accSpawn += tpf;
    const now = Date.now();
    if(guiMsg && now > guiExpire){ Gui.removeWord(guiMsg); guiMsg = null; }
    // try spawn every 5 seconds
    if(accSpawn >= 5.0){ try{ trySpawnNearPlayer(); }catch(e){console.error("Spawn error:", e); } accSpawn = 0; }
    // smooth movement every frame
    try{ updateMovementSmooth(tpf); }catch(e){ console.error("move smooth error:", e); }
    if(first){ updateStatsGui(); first = false; }
  }, "Update");

  // stats GUI click handler
  let statsHandle = null;
  function updateStatsGui(){
    const txt = "Kills: " + kills + " | NPCs: " + Object.keys(npcs).length + " (click to view)";
    if(statsHandle) Gui.removeWord(statsHandle);
    statsHandle = Gui.guiWord(txt, 0.02, 0.98, 0, 14, "npc_stats");
    Gui.setColor(statsHandle, 1,1,0.6,1);
  }
  updateStatsGui();

  Engine.onTick(function(tpf, tag){
    // when stats GUI clicked, show a transient message
    if(tag === "npc_stats") showGui("Stats — Kills: " + kills + " | NPCs: " + Object.keys(npcs).length);
  }, "npc_stats");

})();
