// Utilities — always-on player coordinate readout, plus a set/teleport
// waypoint pair. Note: there's no text-input GUI widget yet, so "teleport
// to specific coords" works via set-here-then-go-there rather than typed
// numbers. Swap this for a real coordinate input if that gets added later.

globalThis.LocUtils = {
    waypoint: null,
};

Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    Gui.guiWord(
        "XYZ: " + p[0].toFixed(1) + ", " + p[1].toFixed(1) + ", " + p[2].toFixed(1),
        0.15, 0.9, 0, 0.022, "loc_readout"
    );
}, "Update");

function waypointLabel() {
    return LocUtils.waypoint
        ? "Waypoint: " + LocUtils.waypoint.join(", ") + " (click to update)"
        : "No waypoint set — click to set one here";
}

Gui.guiWord(waypointLabel(), 0.15, 0.85, 0, 0.02, "loc_setWaypoint");
Gui.guiWord("Teleport to Waypoint", 0.15, 0.8, 0, 0.025, "loc_teleport");

Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    LocUtils.waypoint = [Math.round(p[0]), Math.round(p[1]), Math.round(p[2])];
    Gui.guiWord(waypointLabel(), 0.15, 0.85, 0, 0.02, "loc_setWaypoint");
}, "loc_setWaypoint");

Engine.onTick(function(tpf, tag) {
    if (!LocUtils.waypoint) return;
    Player.setPosition(LocUtils.waypoint[0], LocUtils.waypoint[1], LocUtils.waypoint[2]);
}, "loc_teleport");
