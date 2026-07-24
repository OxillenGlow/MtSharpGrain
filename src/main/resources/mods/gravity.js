// ============================================================================
// gravity.js — MarsGravity
//
// NOTE: py (Player.getPosition()[1]) is the HEAD/camera position, not the
// feet. The player occupies 2 vertical cells: py (head) and py-1 (feet).
//
// Every tick (while gravity is enabled) it checks three cells relative to
// the player's head position (py):
//
//   head       = py       (the head/camera cell itself)
//   feet       = py - 1
//   groundCell = py - 2    ("-2 beneath the player" — first cell below feet)
//
// State machine, checked in this order (first match wins):
//   1. head AND feet both solid   -> embedded in terrain, push up.
//   2. head solid, feet clear     -> suffocating, damage + snap to lastOkLocation.
//   3. feet solid, head clear     -> something intersected your feet, snap back.
//   4. groundCell is air          -> nothing to stand on, accelerate + fall.
//   5. otherwise                  -> resting; if this is a normal standing
//                                     pose (groundCell solid), remember it
//                                     as lastOkLocation.
//
// Gravity toggle only disables steps 3 and 4 (the fall/feet-block checks).
// Stuck-push (1) and suffocation (2) stay active even with gravity off.
// ============================================================================

var G_MARS = 3.711;               // m/s^2 — Mars surface gravity
var STUCK_RISE_SPEED = 0.5;       // m/s — push-out speed when embedded in ground
var DEATH_TIME_SECONDS = 20.0;    // continuous head-collision kills you in this long
var MAX_FALL_STEP_PER_TICK = 1.0; // blocks — caps how far one tick can move you,
                                   // so fast fall speeds can never skip past the
                                   // feet/ground checks in a single frame

var SAVE_LOCATION = "gravityState";

var lastOkLocation = null; // [x, y, z]
var gravityEnabled = true;
var fallVelocity = 0;
var firstFrame = true;

function toBlockCoord(v) {
    return Math.floor(v + 0.5);
}

// ── Persistence ─────────────────────────────────────────────────────────
// One JSON blob holds both fields — same "in-memory buffer, flush on close"
// pattern as every other Data.save() call in this codebase.

function loadState() {
    var raw = Data.get(SAVE_LOCATION);
    if (!raw) return;
    try {
        var parsed = JSON.parse(raw);
        if (parsed.lastOkLocation) lastOkLocation = parsed.lastOkLocation;
        if (typeof parsed.gravityEnabled === "boolean") gravityEnabled = parsed.gravityEnabled;
    } catch (e) {
        // corrupt/missing save — just fall back to the defaults above
    }
}

function saveState() {
    Data.save(JSON.stringify({
        lastOkLocation: lastOkLocation,
        gravityEnabled: gravityEnabled
    }), SAVE_LOCATION);
}

// ── Helpers ──────────────────────────────────────────────────────────────

function isSolid(blockId) {
    return blockId !== 0 && blockId !== 1; // not air, not reserved
}

function teleportToLastOk() {
    if (!lastOkLocation) return; // nothing safe recorded yet — nothing we can do
    Player.setPosition(lastOkLocation[0], lastOkLocation[1], lastOkLocation[2]);
}

function rememberSafeSpot(x, y, z) {
    lastOkLocation = [x, y, z];
    saveState();
}

function sendHeartLoss(tpf) {
    var percentage = (tpf / DEATH_TIME_SECONDS) * 100;
    Mod.send(JSON.stringify({
        messageType: "looseHeartPoints",
        percentage: percentage
    }));
}

// ── Gravity toggle button ───────────────────────────────────────────────

function gravityLabel() {
    return "Gravity: " + (gravityEnabled ? "ON" : "OFF");
}

function drawGravityButton() {
    var handle = Gui.guiWord(gravityLabel(), 0.85, 0.9, 0, 0.025, "gravityToggle");
    Gui.setColor(handle,
        gravityEnabled ? 0.3 : 0.9,
        gravityEnabled ? 1.0 : 0.3,
        0.3, 1.0);
}

drawGravityButton();

Engine.onTick(function (tpf, tag) {
    gravityEnabled = !gravityEnabled;
    fallVelocity = 0; // fresh start either way — don't carry stale fall speed across the toggle
    if (gravityEnabled) {
        // player may have flown somewhere new while gravity was off —
        // treat wherever they are right now as the new safe spot
        var p = Player.getPosition();
        rememberSafeSpot(p[0], p[1], p[2]);
    } else {
        saveState();
    }
    drawGravityButton();
}, "gravityToggle");

// ── Main physics tick ────────────────────────────────────────────────────

Engine.onTick(function (tpf, tag) {
    if (firstFrame) {
        firstFrame = false;
        loadState();
        drawGravityButton(); // re-draw in case the loaded state changed the label
        var p = Player.getPosition();
        if (!lastOkLocation) rememberSafeSpot(p[0], p[1], p[2]); // nothing saved yet — start here
        return; // skip physics this frame — just finished loading
    }

    var p = Player.getPosition();
    var px = toBlockCoord(p[0]); // Round not floor
    var py = toBlockCoord(p[1]); // HEAD position
    var pz = toBlockCoord(p[2]);

    var head = Block.get(px, py, pz);         // the head/camera cell itself
    var feet = Block.get(px, py - 1, pz);      // one below head
    var groundCell = Block.get(px, py - 2, pz); // "-2 beneath the player"

    var headSolid = isSolid(head);
    var feetSolid = isSolid(feet);

    // 1. Embedded in terrain (both head and feet blocked) — always checked.
    if (headSolid && feetSolid) {
        fallVelocity = 0;
        Player.setPosition(p[0], p[1] + STUCK_RISE_SPEED * tpf, p[2]);
        return;
    }

    // 2. Suffocating (head blocked, feet clear) — always checked.
    if (headSolid) {
        fallVelocity = 0;
        teleportToLastOk();
        sendHeartLoss(tpf);
        return;
    }

    // Gravity fully off past this point — no feet-block snap, no falling.
    if (!gravityEnabled) {
        return;
    }

    // 3. Feet blocked (head clear) — something intersected your feet, snap back.
    if (feetSolid) {
        fallVelocity = 0;
        teleportToLastOk();
        return;
    }

    // 4. Nothing solid at groundCell — keep falling, speeding up.
    if (!isSolid(groundCell)) {
        fallVelocity += G_MARS * tpf;
        var dy = Math.min(fallVelocity * tpf, MAX_FALL_STEP_PER_TICK);
        Player.setPosition(p[0], p[1] - dy, p[2]);
        return;
    }

    // 5. Resting on solid ground — safe spot, remember it.
    fallVelocity = 0;
    rememberSafeSpot(p[0], p[1], p[2]);
}, "Update");
