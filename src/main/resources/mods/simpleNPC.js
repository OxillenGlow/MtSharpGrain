// MtSharpGrain JS NPC mod — minimal fixes applied (stats click, createRectangle use, movement & chunk checks)
// Beware, AI generated (might work, might)
(function(){
  const MOD = "simple_npcs";
  let npcs = {}, partToMain = {}, chunkCounts = {}, nextId = 1;
  let kills = 0, accSpawn = 0, accMove = 0, guiMsg = null, guiExpire = 0;
  // load saved kills
  try{ const s = Data.get("kills_v1"); if(s) kills = parseInt(JSON.parse(s).kills||0,10) || 0; }catch(e){ kills = 0; }

  function saveKills(){ Data.save(JSON.stringify({kills:kills}), "kills_v1"); }
  function chunkOf(x,y,z){ let cx=Math.floor(x/32), cy=Math.floor(y/32), cz=Math.floor(z/32); return {cx,cy,cz,key:cx+","+cy+","+cz}; }
  function incChunk(key,d){ chunkCounts[key]=(chunkCounts[key]||0)+d; if(chunkCounts[key]<=0) delete chunkCounts[key]; }
  function rndFloat(a,b){ return a + Math.random()*(b-a); }
  function int(v){ return Math.floor(v); }
  function showGui(msg){ if(guiMsg) Gui.removeWord(guiMsg); guiMsg = Gui.guiWord(msg,0.5,0.9,0,20,"npc_hit_msg"); Gui.setColor(guiMsg,1,1,1,1); Gui.toTop(guiMsg); guiExpire = Date.now()+1500; }

  // create rectangle helper:
  // Create the rectangle (with a reasonable small size), attach it under the given parent handle,
  // then set the child's local (relative) position offset. Store the returned handle in the parts
  // array and map the handle -> mainName so click handlers resolve correctly.
  function mkRectAttached(parentHandle, mainName, suffix, ox, oy, oz, size, color, parts){
    const pname = mainName + ":" + suffix;
    // size is an array [sx,sy,sz]
    const p = Scene.createRectangle(pname, size[0], size[1], size[2]);
    Scene.setColor(p, color[0], color[1], color[2], 1);
    Scene.attachChild(parentHandle, p);
    // set local offset relative to parent
    Scene.setRelativePosition(p, ox, oy, oz);
    parts.push(p);
    // map both the handle and the name string (in case the engine reports either) to the NPC mainName
    partToMain[p] = mainName;
    partToMain[pname] = mainName;
  }
  function mainNameHandle(mainName){
    if(npcs[mainName] && npcs[mainName].main) return npcs[mainName].main;
    // fallback: create a node handle we can attach to (rare)
    return Scene.createNode(mainName);
  }

  function createNPCOfType(type,x,y,z){
    const id = nextId++;
    const mainName = MOD + "_npc#" + id;
    const main = Scene.createNode(mainName);
    Scene.setPosition(main, x, y, z);
    Scene.attachChild(0, main); 
    let parts = [];
    // map the main node handle -> mainName so clicks on the root also resolve
    partToMain[main] = mainName;
    partToMain[mainName] = mainName;
    // assemble visuals (createRectangle takes SIZE not position; use setRelativePosition for offsets)
    if(type === "drone" || type === "attack_drone" || type === "bomber"){
      // body + 4 rotors
      const cols = type==="attack_drone"?[0.8,0.3,0.3] : (type==="bomber"?[0.6,0.1,0.6] : [0.4,0.4,0.9]);
      const bodySize = [0.8, 0.2, 0.6];
      const rotorSize = [0.3, 0.05, 0.3];
      mkRectAttached(main, mainName, "body", 0, 0, 0, bodySize, cols, parts);
      mkRectAttached(main, mainName, "r1", 0.6, 0.2, 0, rotorSize, [0.2,0.2,0.2], parts);
      mkRectAttached(main, mainName, "r2", -0.6, 0.2, 0, rotorSize, [0.2,0.2,0.2], parts);
      mkRectAttached(main, mainName, "r3", 0, 0.2, 0.6, rotorSize, [0.2,0.2,0.2], parts);
      mkRectAttached(main, mainName, "r4", 0, 0.2, -0.6, rotorSize, [0.2,0.2,0.2], parts);
    } else if(type === "rover"){
      const chassisSize = [0.9, 0.3, 0.6];
      const wheelSize = [0.4, 0.2, 0.4];
      const headSize = [0.4, 0.3, 0.4];
      mkRectAttached(main, mainName, "chassis", 0, 0, 0, chassisSize, [0.3,0.8,0.3], parts);
      mkRectAttached(main, mainName, "wheel1", 0.5, -0.25, 0.5, wheelSize, [0.1,0.1,0.1], parts);
      mkRectAttached(main, mainName, "wheel2", -0.5, -0.25, 0.5, wheelSize, [0.1,0.1,0.1], parts);
      mkRectAttached(main, mainName, "head", 0, 0.4, 0, headSize, [0.9,0.9,0.2], parts);
    } else if(type==="blob"){
      const massSize = [0.7, 0.5, 0.7];
      const eyeSize = [0.15, 0.15, 0.15];
      mkRectAttached(main, mainName, "mass", 0, 0, 0, massSize, [0.2,0.9,0.5], parts);
      mkRectAttached(main, mainName, "eye", 0, 0.35, 0.2, eyeSize, [0,0,0], parts);
    } else {
      const bodySize = [0.6, 0.4, 0.6];
      const p = Scene.createRectangle(mainName + ":body", bodySize[0], bodySize[1], bodySize[2]);
      Scene.setColor(p,1,1,1,1);
      Scene.attachChild(main, p);
      Scene.setRelativePosition(p, 0, 0, 0);
      parts.push(p);
      partToMain[p] = mainName;
      partToMain[mainName + ":body"] = mainName;
    }
    const ck = chunkOf(x,y,z).key;
    npcs[mainName] = {id, mainName, main, parts, type, px:int(x), py:int(y), pz:int(z), chunk:ck};
    incChunk(ck,1);
    updateStatsGui();
    return npcs[mainName];
  }

  function destroyNPC(mainName){
    const info = npcs[mainName]; if(!info) return;
    for(let p of info.parts){ try{ Scene.destroy(p); delete partToMain[p]; }catch(e){} }
    try{ Scene.destroy(info.main); }catch(e){}
    incChunk(info.chunk,-1);
    delete npcs[mainName];
    updateStatsGui();
  }

  function degradeToDrone(info){
    for(let p of info.parts){ try{ Scene.destroy(p); delete partToMain[p]; }catch(e){} }
    info.parts = [];
    info.type = "drone";
    const pos = Scene.getPosition(info.main); const x = pos[0], y = pos[1], z = pos[2];
    // rebuild as a basic drone (body + 4 rotors) using same sizes as createNPCOfType
    const bodySize = [0.8, 0.2, 0.6];
    const rotorSize = [0.3, 0.05, 0.3];
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

  // spawn logic (5s)
  function trySpawnNearPlayer(){
    console.log("spawning");
    const ppos = Player.getPosition(); const px=int(ppos[0]), py=int(ppos[1]), pz=int(ppos[2]);
    const base = chunkOf(px,py,pz); let spawned = 0;
    let droneBias = Math.min(0.25 + kills*0.01, 0.6);
    let chunks = [];
    for(let dx=-2;dx<=2;dx++) for(let dy=-1;dy<=1;dy++) for(let dz=-2;dz<=2;dz++) chunks.push({cx:base.cx+dx,cy:base.cy+dy,cz:base.cz+dz});
    for(let i=chunks.length-1;i>0;i--){ let j=Math.floor(Math.random()*(i+1)); [chunks[i],chunks[j]]=[chunks[j],chunks[i]]; }
    for(const c of chunks){
      if(spawned>=2) break;
      const cx=c.cx, cy=c.cy, cz=c.cz;
      const centerX = cx*32 + 16, centerY = cy*32 + 16, centerZ = cz*32 + 16;
      if(Block.get(centerX, centerY, centerZ) !== 0) continue;
      if(Block.get(centerX, centerY-1, centerZ) === 0) continue;
      const key = cx+","+cy+","+cz;
      if((chunkCounts[key]||0) >= 1) continue;
      if(Math.random() > 0.12 + droneBias*0.1) continue;
      const r = Math.random(); let t;
      if(r < 0.5 + droneBias*0.5) t = "drone";
      else if(r < 0.7) t = "rover";
      else if(r < 0.85) t = "blob";
      else if(r < 0.93) t = "attack_drone";
      else t = "bomber";
      console.log("Made entity at "+centerX + centerY + centerZ); 

      createNPCOfType(t, centerX, centerY, centerZ);
      spawned++;
    }
  }

  // movement (3s) — corrected: require destination air and solid ground below; enforce chunk occupancy before moving
  function tickMovement(){
    for(const mainName in npcs){
      const info = npcs[mainName];
      const pos = Scene.getPosition(info.main);
      const x = int(pos[0]), y = int(pos[1]), z = int(pos[2]);
      let newX = x, newY = y, newZ = z, movedFlag = false;
      const oldChunk = info.chunk;
      if(info.type === "rover" || info.type === "blob"){
        const deltas = [[1,0,0],[-1,0,0],[0,0,1],[0,0,-1],[1,0,1],[1,0,-1],[-1,0,1],[-1,0,-1],[0,0,0]];
        for(let i=deltas.length-1;i>0;i--){ let j=Math.floor(Math.random()*(i+1)); [deltas[i],deltas[j]]=[deltas[j],deltas[i]]; }
        for(const d of deltas){
          const tx = x + d[0], ty = y + d[1], tz = z + d[2];
          if(Block.get(tx,ty,tz) !== 0) continue; // destination must be air
          if(Block.get(tx,ty-1,tz) === 0) continue; // must have solid ground below
          const ck = chunkOf(tx,ty,tz).key;
          if(ck !== oldChunk && (chunkCounts[ck]||0) >= 1) continue; // avoid overcrowding
          // move
          Scene.setPosition(info.main, tx, ty, tz);
          newX = tx; newY = ty; newZ = tz; movedFlag = true; break;
        }
        if(!movedFlag){
          // try moving down one step if valid
          if(Block.get(x,y-1,z) === 0 && Block.get(x,y-2,z) !== 0){
            const tx = x, ty = y-1, tz = z; const ck = chunkOf(tx,ty,tz).key;
            if(ck === oldChunk || (chunkCounts[ck]||0) < 1){
              Scene.setPosition(info.main, tx, ty, tz);
              newX = tx; newY = ty; newZ = tz;
            }
          }
        }
      } else if(info.type === "drone" || info.type === "attack_drone" || info.type === "bomber"){
        let tries = 6;
        while(tries--){
          const dx = Math.floor(rndFloat(-2,2)), dy = Math.floor(rndFloat(-1,2)), dz = Math.floor(rndFloat(-2,2));
          const tx = x + dx, ty = y + dy, tz = z + dz;
          if(ty < 1 || ty > 200) continue;
          if(Block.get(tx,ty,tz) !== 0) continue; // must be air
          const ck = chunkOf(tx,ty,tz).key;
          if(ck !== oldChunk && (chunkCounts[ck]||0) >= 1) continue;
          Scene.setPosition(info.main, tx, ty, tz); newX = tx; newY = ty; newZ = tz; break;
        }
      }
      const newChunk = chunkOf(newX,newY,newZ).key;
      if(newChunk !== oldChunk){ incChunk(oldChunk,-1); incChunk(newChunk,1); info.chunk = newChunk; }
      info.px = newX; info.py = newY; info.pz = newZ;
    }
    updateStatsGui();
  }

  let first = true;
  Engine.onTick(function(tpf,tag){
    accMove += tpf; accSpawn += tpf;
    const now = Date.now();
    if(guiMsg && now > guiExpire){ Gui.removeWord(guiMsg); guiMsg = null; }
    if(accMove >= 3.0){ try{ tickMovement(); }catch(e){console.error("Move error:", e); } accMove = 0; }
    if(accSpawn >= 5.0){ try{ trySpawnNearPlayer(); }catch(e){console.error("Spawn error:", e); } accSpawn = 0; }
    if(first){
      updateStatsGui(); 
      first = false;
    }
  }, "Update");

  // stats GUI: click shows stats instead of resetting (minimum fix)
  let statsHandle = null;
  function updateStatsGui(){
    const txt = "Kills: " + kills + " | NPCs: " + Object.keys(npcs).length + " (click to view)";
    if(statsHandle) Gui.removeWord(statsHandle);
    statsHandle = Gui.guiWord(txt, 0.02, 0.98, 0, 14, "npc_stats");
    Gui.setColor(statsHandle, 1,1,0.6,1);
  }
  updateStatsGui();

  Engine.onTick(function(tpf, tag){
    // when stats GUI clicked, this fires (tag "npc_stats"); show a transient message instead of destructive reset
    showGui("Stats — Kills: " + kills + " | NPCs: " + Object.keys(npcs).length);
  }, "npc_stats");

})();
