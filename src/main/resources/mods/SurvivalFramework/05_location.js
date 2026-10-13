// ============================================================================
// 05_location.js — Deadly-Inventory

var LOCATION_SAVE_LOCATION = "playerLocation";
var LOCATION_SAVE_INTERVAL = 5.0; // seconds between writes

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

var l = loadLocation();
Player.setPosition(l.x, l.y, l.z);

Engine.setInterval(function() {
    saveLocation();
},LOCATION_SAVE_INTERVAL);
