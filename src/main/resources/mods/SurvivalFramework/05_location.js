// ============================================================================
// 05_location.js — Deadly-Inventory
//
// Persists the player's last-known world position via the Data API, as a
// small JSON blob under key "playerLocation":
//   { "x": <float>, "y": <float>, "z": <float> }
//
// Restoration happens on the FIRST "Update" tick, not at script-load time.
// This matters because Main.simpleInitApp() sets a hardcoded spawn
// (cam.setLocation(spawn)) AFTER mod packs are loaded/evaluated — if we
// restored at load time (top-level script code), Main's hardcoded spawn
// would run afterward and silently overwrite it. Deferring to the first
// tick guarantees we run after simpleInitApp() has finished, so the
// restored position actually sticks.
//
// Saved periodically thereafter (every LOCATION_SAVE_INTERVAL seconds) —
// Data.save() only writes to the in-memory buffer; the actual disk flush
// happens once at shutdown via DataApi.save() (JSModifier.onClose ->
// ModPackManager.onClose -> Main.destroy()).
// ============================================================================

var LOCATION_SAVE_LOCATION = "playerLocation";
var LOCATION_SAVE_INTERVAL = 5.0; // seconds between writes

var locationSaveTimer = 0;
var firstframe = true;

function loadLocation() {
    var raw = Data.get(LOCATION_SAVE_LOCATION);
    if (!raw) return null;
    try {
        return JSON.parse(raw);
    } catch (e) {
        return null;
    }
}

function saveLocation() {
    var p = Player.getPosition();
    Data.save(JSON.stringify({ x: p[0], y: p[1], z: p[2] }), LOCATION_SAVE_LOCATION);
}

Engine.onTick(function (tpf, tag) {
    if (firstframe) {
        firstframe = false;
        var loc = loadLocation();
        if (loc && typeof loc.x === "number" && typeof loc.y === "number" && typeof loc.z === "number") {
            Player.setPosition(loc.x, loc.y, loc.z);
        }
        return; // skip the save-timer logic this frame — nothing to save yet
    }

    locationSaveTimer += tpf;
    if (locationSaveTimer >= LOCATION_SAVE_INTERVAL) {
        locationSaveTimer = 0;
        saveLocation();
    }
}, "Update");
