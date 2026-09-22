// LFTstatus — always-on left HUD: coordinates, altitude, and a simple
// "on ground / airborne" indicator. Prefix LFT makes it draw in play mode too.
// Keep footprint small (left ~15% of screen).

globalThis.LFTstatus = {
    lastY: null,
    airborneFrames: 0
};

Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    var x = p[0];
    var y = p[1];
    var z = p[2];

    Gui.guiWord("XYZ " + x + "  " + y + "  " + z, 0.5, 0.92, 0, 0.010, "lft_xyz");
}, "Update");
