// ============================================================================
// 03_death.js — Deadly-Inventory Survival Framework
//
// Listens for json broadcasts sent by ANY pack via
// Mod.send() (playerId is ignored for now — single-player but please add the field as a stub anyways; reason uses
// underscores instead of spaces, e.g. "fell_into_lava", and is de-underscored
// for display).
//
// On death: lift the player clear of whatever killed them, show a big
// "You Died because: <reason>" message, then after a delay teleport them to
// a random point in a ring around (10000, ?, 0), scanning downward from
// y = 40 for the first solid ground.
// ============================================================================

var DEATH_GUI_TAG = "deadlyInv_deathMsg";
var DEATH_LIFT_HEIGHT = 8;       // blocks — lifted out of immediate danger
var DEATH_RESPAWN_DELAY = 10.0;  // seconds
var RESPAWN_CENTER_X = 10000;
var RESPAWN_CENTER_Z = 0;
var RESPAWN_RADIUS = 60;
var RESPAWN_START_Y = 40;
var RESPAWN_MIN_Y = -100;

var deathActive = false;
var deathTimer = 0.0;
var deathMsgHandle = -1;
var lastReason;
var heartPoints = 100;

// Handles JSON messages of shape: { messageType: "die", playerId: ..., reason: "fell_into_lava" }
// Anything that isn't valid JSON, or doesn't have messageType === "die", is silently ignored —
// this pack shares the Mod.send() channel with every other pack, so most messages aren't for us.
function onReceive(data, fromPack) {
    var msg;
    try {
        msg = JSON.parse(data);
    } catch (e) {
        return; // not JSON — not meant for us
    }
    if (!msg || (msg.messageType !== "die" && msg.messageType !== "looseHeartPoints")) return;
    if (msg.messageType == "die") {    // reason keeps the same underscore-separated convention as before, just carried in a JSON field now.
        var reason = (msg.reason || "unknown").split("_").join(" ");
        triggerDeath(reason);
    } else {
        heartPoints -= (msg.percentage || 10);
        lastReason = (msg.reason || "unknown");
    }
}

function triggerDeath(reason) {
    var p = Player.getPosition();
    Player.setPosition(p[0], p[1] + DEATH_LIFT_HEIGHT, p[2]);

    deathActive = true;
    deathTimer = DEATH_RESPAWN_DELAY;

    if (deathMsgHandle >= 0) Gui.removeWord(deathMsgHandle);
    deathMsgHandle = Gui.guiWord("You Died because: " + reason, 0.5, 0.6, 0, 0.06, DEATH_GUI_TAG);
}

/** Scans downward from RESPAWN_START_Y; a cell is "ground" once the block
 *  two below it is no longer air (id 0). Capped at RESPAWN_MIN_Y. */
function findGroundY(x, z) {
    var y = RESPAWN_START_Y;
    while (y > RESPAWN_MIN_Y) {
        var below = Scene.getBlockId(x, y - 2, z);
        if (below !== 0) return y;
        y -= 1;
    }
    return RESPAWN_MIN_Y;
}

function respawnPlayer() {
    var angle = Math.random() * Math.PI * 2;
    var radius = Math.random() * RESPAWN_RADIUS;
    var x = Math.round(RESPAWN_CENTER_X + Math.cos(angle) * radius);
    var z = Math.round(RESPAWN_CENTER_Z + Math.sin(angle) * radius);
    var y = findGroundY(x, z);

    Player.setPosition(x, y, z);
}

function loadHeartPoints() {return;}// not ready yet
var firstframe = true;
Engine.onTick(function (tpf, tag) {
    if (firstframe2){
        firstframe2 = false;
        loadHeartPoints();
    }
    if (!deathActive) return;
    deathTimer -= tpf;
    if (deathTimer <= 0) {
        deathActive = false;
        if (deathMsgHandle >= 0) {
            Gui.removeWord(deathMsgHandle);
            deathMsgHandle = -1;
        }
        respawnPlayer();
    }

    if (heartPoints < 0) {triggerDeath(lastReason)}
}, "Update");
