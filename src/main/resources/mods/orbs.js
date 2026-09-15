// lightOrbs — interactive light placement mod (menu-only, no prefix).
// Click buttons to spawn temporary colored light orbs near the player.
// Labels are created once (persistent until destruction).
// Only the lifetime / bob animation runs on "Update".

globalThis.LightOrbs = {
    active: [],
    maxOrbs: 12
};

function spawnOrb(r, g, b, nameSuffix) {
    var p = Player.getPosition();
    var handle = Scene.createLight("orb_" + nameSuffix + "_" + Date.now(), r, g, b, 8.0);
    Scene.attachChild(0, handle);

    // slight random offset so multiple orbs don't stack exactly
    var ox = (Math.random() - 0.5) * 3;
    var oy = 1.5 + Math.random() * 2;
    var oz = (Math.random() - 0.5) * 3;
    Scene.setPosition(handle, p[0] + ox, p[1] + oy, p[2] + oz);

    LightOrbs.active.push({
        handle: handle,
        life: 25.0,          // seconds
        x: p[0] + ox,
        y: p[1] + oy,
        z: p[2] + oz,
        bobPhase: Math.random() * Math.PI * 2
    });

    // trim oldest if over limit
    while (LightOrbs.active.length > LightOrbs.maxOrbs) {
        var old = LightOrbs.active.shift();
        Scene.destroy(old.handle);
    }
}

// Static labels — created once at load. No need to redraw every frame.
Gui.guiWord("[ Red Light ]",   0.55, 0.40, 0, 0.028, "orb_red");
Gui.guiWord("[ Green Light ]", 0.55, 0.35, 0, 0.028, "orb_green");
Gui.guiWord("[ Blue Light ]",  0.55, 0.30, 0, 0.028, "orb_blue");
Gui.guiWord("[ White Light ]", 0.55, 0.25, 0, 0.028, "orb_white");
Gui.guiWord("[ Clear All Orbs ]", 0.55, 0.18, 0, 0.025, "orb_clear");

// Click handlers (only fire when the matching GUI tag is clicked)
Engine.onTick(function(tpf, tag) { spawnOrb(1.0, 0.2, 0.15, "red");   }, "orb_red");
Engine.onTick(function(tpf, tag) { spawnOrb(0.2, 1.0, 0.3, "green"); }, "orb_green");
Engine.onTick(function(tpf, tag) { spawnOrb(0.25, 0.4, 1.0, "blue"); }, "orb_blue");
Engine.onTick(function(tpf, tag) { spawnOrb(1.0, 0.95, 0.85, "white"); }, "orb_white");

Engine.onTick(function(tpf, tag) {
    for (var i = 0; i < LightOrbs.active.length; i++) {
        Scene.destroy(LightOrbs.active[i].handle);
    }
    LightOrbs.active = [];
}, "orb_clear");

// Lifetime + gentle bob — only this runs every frame
Engine.onTick(function(tpf, tag) {
    for (var i = LightOrbs.active.length - 1; i >= 0; i--) {
        var o = LightOrbs.active[i];
        o.life -= tpf;
        o.bobPhase += tpf * 1.5;
        var by = o.y + Math.sin(o.bobPhase) * 0.25;
        Scene.setPosition(o.handle, o.x, by, o.z);
        if (o.life <= 0) {
            Scene.destroy(o.handle);
            LightOrbs.active.splice(i, 1);
        }
    }
}, "Update");
