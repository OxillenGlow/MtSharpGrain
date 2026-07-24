// ============================================================================
// gravity.js — MarsGravity
//
// NOTE: py (Player.getPosition()[1]) is the HEAD/camera position, not the
// feet. The player occupies 2 vertical cells: py (head) and py-1 (feet).
//
// Block coordinates use ROUND-to-nearest (Math.floor(v + 0.5)), matching
// BlockSelector.java's Math.floor(v + 0.5) convention — blocks are centered
// on their integer coordinate, not "floor-aligned" like most voxel games.
//
// Every tick (while gravity is enabled) it checks three cells relative to
// the player's head position (py):
//
//   head       = py       (the head/camera cell itself)
//   feet       = py - 1
//   groundCell = py - 2    ("-2 beneath the player")
//
// State machine, checked in this order (first match wins):
//   1. head AND feet both solid  -> embedded in terrain, push up (0.5 m/s).
//   2. head solid, feet clear    -> suffocating, damage + snap to lastOkLocation.
//   [gravity-off checks stop here]
//   3. feet solid, head clear    -> STEP-UP assist: ease upward onto whatever
//                                    is at your feet, instead of teleporting
//                                    you backward. This covers BOTH normal
//                                    terrain bumps while walking AND landing
//                                    softly after a fall — same physical
//                                    situation, same fix.
//   4. groundCell is air         -> nothing to stand on, accelerate + fall
//                                    (signed verticalVelocity, Mars gravity).
//   5. otherwise                 -> resting; remember lastOkLocation; mark
//                                    "grounded" so the Jump button will work.
//
// Jump is a real mechanic now (button, same pattern as the gravity toggle)
// instead of relying on flyCam's own vertical-fly keys — those fight this
// script for control of your vertical position every frame, which is what
// made the old "jump via flyCam" feel rough. See the bottom of this file
// (and the optional Java patch mentioned alongside it) if you want flyCam's
// Q/Z vertical keys fully disabled too.
// ============================================================================

var G_MARS = 3.711;               // m/s^2 — Mars surface gravity
var STUCK_RISE_SPEED = 0.5;       // m/s — push-out speed when embedded in ground
var STEP_UP_SPEED = 8;          // m/s — how fast you ease up onto a block at your feet
var JUMP_SPEED = 6;              // m/s — initial upward velocity on jump
var DEATH_TIME_SECONDS = 20.0;    // continuous head-collision kills you in this long
var MAX_MOVE_PER_TICK = 1.0;      // blocks — caps how far ANY single tick can move you,
                                   // so fast speeds (falling or jumping) can never skip
                                   // past the feet/ground checks in one frame

var SAVE_LOCATION = "gravityState";

var lastOkLocation = null; // [x, y, z]
var gravityEnabled = false;
var verticalVelocity = 0;  // signed: positive = moving up, negative = moving down
var grounded = false;      // true only right after a normal "resting" tick — gates jumping
var firstFrame = true;

// ── Coordinate helper ─────────────────────────────────────────────────────

function toBlockCoord(v) {
    return Math.floor(v + 0.5); // round-to-nearest, matches BlockSelector.java
}

function clampMove(dy) {
    if (dy > MAX_MOVE_PER_TICK) return MAX_MOVE_PER_TICK;
    if (dy < -MAX_MOVE_PER_TICK) return -MAX_MOVE_PER_TICK;
    return dy;
}

// ── Persistence ─────────────────────────────────────────────────────────

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
    verticalVelocity = 0;
    grounded = false;
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

// ── Jump button ────────────────────────────────────────────────────────

Gui.guiWord("Jump", 0.85, 0.85, 0, 0.025, "jumpButton");

Engine.onTick(function (tpf, tag) {
    if (!gravityEnabled || !grounded) return; // can't jump mid-air or with gravity off
    verticalVelocity = JUMP_SPEED;
    grounded = false;
}, "jumpButton");

// ── Main physics tick ────────────────────────────────────────────────────

Engine.onTick(function (tpf, tag) {
    if (firstFrame) {
        firstFrame = false;
        loadState();
        drawGravityButton(); // re-draw in case the loaded state changed the label
        var p0 = Player.getPosition();
        if (!lastOkLocation) rememberSafeSpot(p0[0], p0[1], p0[2]); // nothing saved yet — start here
        return; // skip physics this frame — just finished loading
    }

    var p = Player.getPosition();
    var px = toBlockCoord(p[0]);
    var py = toBlockCoord(p[1]); // HEAD position
    var pz = toBlockCoord(p[2]);

    var head = Block.get(px, py, pz);          // the head/camera cell itself
    var feet = Block.get(px, py - 1, pz);       // one below head
    var groundCell = Block.get(px, py - 2, pz); // "-2 beneath the player"

    var headSolid = isSolid(head);
    var feetSolid = isSolid(feet);

    grounded = false; // only the resting branch below sets this back to true

    // 1. Embedded in terrain (both head and feet blocked) — always checked.
    if (headSolid && feetSolid) {
        verticalVelocity = 0;
        Player.setPosition(p[0], p[1] + STUCK_RISE_SPEED * tpf, p[2]);
        return;
    }

    // 2. Suffocating (head blocked, feet clear) — always checked.
    if (headSolid) {
        verticalVelocity = 0;
        teleportToLastOk();
        sendHeartLoss(tpf);
        return;
    }

    // Gravity fully off past this point — no step-up, no falling, no jumping.
    if (!gravityEnabled) {
        return;
    }

    // 3. Feet blocked (head clear) — STEP-UP assist. Covers both walking
    //    into a normal terrain bump AND landing softly after a fall. Instead
    //    of teleporting you away, ease you up onto whatever's at your feet.
    if (feetSolid) {
        verticalVelocity = 0;
        Player.setPosition(p[0], p[1] + STEP_UP_SPEED * tpf, p[2]);
        return;
    }

    // 4. Nothing solid at groundCell — falling (or still rising from a jump).
    if (!isSolid(groundCell)) {
        verticalVelocity -= G_MARS * tpf;
        var dy = clampMove(verticalVelocity * tpf);
        Player.setPosition(p[0], p[1] + dy, p[2]);
        return;
    }

    // 5. Resting on solid ground — safe spot, remember it, allow jumping.
    verticalVelocity = 0;
    grounded = true;
    if (!headSolid && !feetSolid) {
        rememberSafeSpot(p[0], p[1], p[2]);
    }
}, "Update");