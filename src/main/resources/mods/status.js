// LFTstatus — always-on left HUD: coordinates, altitude, and a simple
// "on ground / airborne" indicator. Prefix LFT makes it draw in play mode too.
// Keep footprint small (left ~15% of screen).

globalThis.LFTstatus = {
    lastY: null,
    airborneFrames: 0
};

Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    var x = p[0].toFixed(1);
    var y = p[1].toFixed(1);
    var z = p[2].toFixed(1);

    // crude airborne detection via Y delta
    if (LFTstatus.lastY !== null) {
        var dy = p[1] - LFTstatus.lastY;
        if (Math.abs(dy) > 0.05) {
            LFTstatus.airborneFrames = Math.min(30, LFTstatus.airborneFrames + 1);
        } else {
            LFTstatus.airborneFrames = Math.max(0, LFTstatus.airborneFrames - 2);
        }
    }
    LFTstatus.lastY = p[1];

    var state = LFTstatus.airborneFrames > 5 ? "AIR" : "GND";

    Gui.guiWord("XYZ " + x + "  " + y + "  " + z, 0.02, 0.92, 0, 0.022, "lft_xyz");
    Gui.guiWord("Alt " + y + "  [" + state + "]", 0.02, 0.88, 0, 0.020, "lft_alt");
}, "Update");
