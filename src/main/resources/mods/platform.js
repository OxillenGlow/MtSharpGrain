// platformBuilder — quick platform / floating island helper.
// Click "Mark Center" then "Build 5x5 Platform" (or larger) to force-set
// metal blocks in a flat square under / around the player.
// Uses forceSet so inventory is not touched.

globalThis.PlatformBuilder = {
    center: null,
    size: 5,
    blockId: 9   // Metal Block
};

function centerLabel() {
    return PlatformBuilder.center
        ? "Center @ " + PlatformBuilder.center.join(",") + " (click to re-mark)"
        : "Click to mark platform center here";
}

Gui.guiWord(centerLabel(), 0.25, 0.42, 0, 0.024, "plat_mark");
Gui.guiWord("Build 5x5 Platform", 0.25, 0.36, 0, 0.026, "plat_5");
Gui.guiWord("Build 9x9 Platform", 0.25, 0.31, 0, 0.026, "plat_9");
Gui.guiWord("Build 15x15 Platform", 0.25, 0.26, 0, 0.026, "plat_15");

Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    PlatformBuilder.center = [
        Math.floor(p[0]),
        Math.floor(p[1]) - 1,   // one below feet so you stand on it
        Math.floor(p[2])
    ];
    Gui.guiWord(centerLabel(), 0.25, 0.42, 0, 0.024, "plat_mark");
}, "plat_mark");

function buildPlatform(size) {
    if (!PlatformBuilder.center) {
        Gui.guiWord("Mark a center first!", 0.25, 0.36, 0, 0.026, "plat_5");
        return;
    }
    var c = PlatformBuilder.center;
    var half = Math.floor(size / 2);
    var id = PlatformBuilder.blockId;

    for (var dx = -half; dx <= half; dx++) {
        for (var dz = -half; dz <= half; dz++) {
            Block.forceSet(c[0] + dx, c[1], c[2] + dz, id);
        }
    }
    Gui.guiWord(size + "x" + size + " platform built!", 0.25, 0.36, 0, 0.026, "plat_5");
}

Engine.onTick(function(tpf, tag) { buildPlatform(5); }, "plat_5");
Engine.onTick(function(tpf, tag) { buildPlatform(9); }, "plat_9");
Engine.onTick(function(tpf, tag) { buildPlatform(15); }, "plat_15");
