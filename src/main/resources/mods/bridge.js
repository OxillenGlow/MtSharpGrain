// Bridge Builder — construction mod.
// Click "Set Start" to mark point A at your current position. Walk to
// where you want the bridge to end, then click "Build Bridge To Here" to
// lay a straight walkway (with side rails) between the two points.

globalThis.BridgeBuilder = {
    startPos: null,     // [x, y, z] or null
    bridgeBlockId: 9,   // Metal Block
    railBlockId: 10,    // Glass
};

function bbLabel() {
    return BridgeBuilder.startPos
        ? "Bridge start @ " + BridgeBuilder.startPos.join(",") + " — click to move it here"
        : "Click to set bridge start point";
}

Gui.guiWord(bbLabel(), 0.2, 0.35, 0, 0.025, "bb_setStart");
Gui.guiWord("Build Bridge To Here", 0.2, 0.3, 0, 0.025, "bb_build");

Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    BridgeBuilder.startPos = [Math.floor(p[0]), Math.floor(p[1]), Math.floor(p[2])];
    Gui.guiWord(bbLabel(), 0.2, 0.35, 0, 0.025, "bb_setStart");
}, "bb_setStart");

Engine.onTick(function(tpf, tag) {
    if (!BridgeBuilder.startPos) {
        Gui.guiWord("Set a start point first!", 0.2, 0.3, 0, 0.025, "bb_build");
        return;
    }

    var p = Player.getPosition();
    var end = [Math.floor(p[0]), Math.floor(p[1]), Math.floor(p[2])];
    var start = BridgeBuilder.startPos;

    var dx = end[0] - start[0];
    var dz = end[2] - start[2];
    var steps = Math.max(Math.abs(dx), Math.abs(dz));

    if (steps === 0) return; // start == end, nothing to build
    if (steps > 500) steps = 500; // sanity cap so a stray click can't hang the game

    for (var i = 0; i <= steps; i++) {
        var t = i / steps;
        var x = Math.round(start[0] + dx * t);
        var z = Math.round(start[2] + dz * t);
        var y = Math.round(start[1] + (end[1] - start[1]) * t);

        Block.place(x, y, z, BridgeBuilder.bridgeBlockId);       // walkway
        Block.place(x, y + 1, z - 1, BridgeBuilder.railBlockId); // rail (assumes a roughly X-aligned bridge)
        Block.place(x, y + 1, z + 1, BridgeBuilder.railBlockId); // rail
    }

    BridgeBuilder.startPos = null;
    Gui.guiWord("Bridge built! Click 'Set Start' to build another.", 0.2, 0.3, 0, 0.025, "bb_build");
    Gui.guiWord("Click to set bridge start point", 0.2, 0.35, 0, 0.025, "bb_setStart");
}, "bb_build");
