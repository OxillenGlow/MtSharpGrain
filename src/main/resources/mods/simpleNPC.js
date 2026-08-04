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

  // create rectangle helper: API is createRectangle(name,x,y,z) — size params ignored by API so we don't pass them
  function mkRectAttached(mainName, suffix, mx,my,mz, ox,oy,oz, color, parts){
    const pname = mainName + ":" + suffix;
    const p = Scene.createRectangle(pname, mx+ox, my+oy, mz+oz);
    Scene.setColor(p, color[0], color[1], color[2], 1);
    Scene.attachChild(mainNameHandle(mainName), p);
    parts.push(p); partToMain[pname] = mainName;
  }
  function mainNameHandle(mainName){ // helper to get main handle from stored npcs or fallback
    if(npcs[mainName] && npcs[mainName].main) return npcs[mainName].main;
    // fallback: search by name? API doesn't provide lookup; assume present
    return npcs[mainName] ? npcs[mainName].main : Scene.createNode(mainName);
  }

  function createNPCOfType(type,x,y,z){
    const id = nextId++;
    const mainName = MOD + "_npc#" + id;
    const main = Scene.createNode(mainName);
    Scene.setPosition(main,x,y,z);
    let parts = [];
    partToMain[mainName] = mainName; // main maps to itself
    // assemble visuals (using fixed createRectangle signature)
    if(type === "drone" || type==="attack_drone" || type==="bomber"){
      // body + 4 rotors
      const cols = type==="attack_drone"?[0.8,0.3,0.3]: (type==="bomber"?[0.6,0.1,0.6]:[0.4,0.4,0.9]);
      const mainH = main;
      const mk = (s,ox,oy,oz,col)=>{ const pname=mainName+":"+s; const p=Scene.createRectangle(pname,x+ox,y+oy,z+oz); Scene.setColor(p,col[0],col[1],col[2],1); Scene.attachChild(main,p); parts.push(p); partToMain[pname]=mainName; };
      mk("body",0,0,0,cols);
      mk("r1",0.6,0.2,0,[0.2,0.2,0.2]); mk("r2",-0.6,0.2,0,[0.2,0.2,0.2]);
      mk("r3",0,0.2,0.6,[0.2,0.2,0.2]); mk("r4",0,0.2,-0.6,[0.2,0.2,0.2]);
    } else if(type === "rover"){
      const mk = (s,ox,oy,oz,col)=>{ const pname=mainName+":"+s; const p=Scene.createRectangle(pname,x+ox,y+oy,z+oz); Scene.setColor(p,col[0],col[1],col[2],1); Scene.attachChild(main,p); parts.push(p); partToMain[pname]=mainName; };
      mk("chassis",0,0,0,[0.3,0.8,0.3]); mk("wheel1",0.5,-0.25,0.5,[0.1,0.1,0.1]); mk("wheel2",-0.5,-0.25,0.5,[0.1,0.1,0.1]); mk("head",0,0.4,0,[0.9,0.9,0.2]);
    } else if(type==="blob"){
      const mk = (s,ox,oy,oz,col)=>{ const pname=mainName+":"+s; const p=Scene.createRectangle(pname,x+ox,y+oy,z+oz); Scene.setColor(p,col[0],col[1],col[2],1); Scene.attachChild(main,p); parts.push(p); partToMain[pname]=mainName; };
      mk("mass",0,0,0,[0.2,0.9,0.5]); mk("eye",0,0.35,0.2,[0,0,0]);
    } else {
      const p = Scene.createRectangle(mainName+":body", x, y, z);
      Scene.setColor(p,1,1,1,1); Scene.attachChild(main,p); parts.push(p); partToMain[mainName+":body"]=mainName;
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
    const mk = (s,ox,oy,oz,col)=>{ const pname=info.mainName+":"+s; const p=Scene.createRectangle(pname,x+ox,y+oy,z+oz); Scene.setColor(p,col[0],col[1],col[2],1); Scene.attachChild(info.main,p); info.parts.push(p); partToMain[pname]=info.mainName; };
    mk("body",0,0,0,[0.4,0.4,0.9]); mk("r1",0.6,0.2,0,[0.2,0.2,0.2]); mk("r2",-0.6,0.2,0,[0.2,0.2,0.2]); mk("r3",0,0.2,0.6,[0.2,0.2,0.2]); mk("r4",0,0.2,-0.6,[0.2,0.2,0.2]);
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

  Engine.onTick(function(tpf){
    accMove += tpf; accSpawn += tpf;
    const now = Date.now();
    if(guiMsg && now > guiExpire){ Gui.removeWord(guiMsg); guiMsg = null; }
    if(accMove >= 3.0){ try{ tickMovement(); }catch(e){} accMove = 0; }
    if(accSpawn >= 5.0){ try{ trySpawnNearPlayer(); }catch(e){} accSpawn = 0; }
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

  Engine.onTick(function(tpf){
    // when stats GUI clicked, this fires (tag "npc_stats"); show a transient message instead of destructive reset
    showGui("Stats — Kills: " + kills + " | NPCs: " + Object.keys(npcs).length);
  }, "npc_stats");

  // expose message receiver (no-op)
  function onReceive(msg,from){}

  // ensure initial GUI update
  Engine.onTick(function(tpf){ updateStatsGui(); }, "InitStats");
})();
