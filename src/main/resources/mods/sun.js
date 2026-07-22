// ============================================================================
// sun.js — orbiting day/night sun.
//
// A PointLight riding an invisible carrier node (Scene.createLight), paired
// with a visible cube (Scene.createCube) tinted to match, orbits the player
// at a fixed 170m radius in a vertical plane.
//
// Angle convention:
//   0°   = dawn   (horizon, dark red, rising)
//   90°  = noon   (zenith, directly overhead, slightly orange)
//   180° = dusk   (horizon, dark red, setting)
//   180°–360°     = night — sun goes fully black (light contributes nothing)
//
// Size: 170 * (real Sun diameter ~1,391,000 km / real Sun-Mars distance
// ~227,900,000 km) ≈ 1.04m cube. Tweak SUN_SIZE directly if you want a
// different real-world reference (radius instead of diameter, etc).
// ============================================================================

var ORBIT_RADIUS = 170;
var SUN_SIZE = 1.04;
var LIGHT_RADIUS = 400; // PointLight falloff — must clear ORBIT_RADIUS to reach the player

// Rotation period options, in seconds, shown as GUI buttons.
var PERIOD_OPTIONS = [
    { label: "1 min",  seconds: 60 },
    { label: "5 min",  seconds: 300 },
    { label: "12 min", seconds: 720 }, // default
    { label: "20 min", seconds: 1200 }
];
var rotationPeriodSeconds = 720;

var angleDeg = 0; // 0 = dawn

var sunCube = Scene.createCube("SunCube", SUN_SIZE);
var sunLight = Scene.createLight("SunLight", 0, 0, 0, LIGHT_RADIUS); // color set every frame below

// ── Color model ─────────────────────────────────────────────────────────
var DAWN_COLOR  = { r: 0.35, g: 0.05, b: 0.0 };
var NOON_COLOR  = { r: 1.0,  g: 0.55, b: 0.15 };
var NIGHT_COLOR = { r: 0.0,  g: 0.0,  b: 0.0 };

function colorForAngle(deg) {
    if (deg > 180 && deg < 360) return NIGHT_COLOR;

    // deg in [0, 180] — triangle wave peaking at 90 (noon)
    var t = 1 - Math.abs(deg - 90) / 90; // 0 at dawn/dusk, 1 at noon
    return {
        r: DAWN_COLOR.r + (NOON_COLOR.r - DAWN_COLOR.r) * t,
        g: DAWN_COLOR.g + (NOON_COLOR.g - DAWN_COLOR.g) * t,
        b: DAWN_COLOR.b + (NOON_COLOR.b - DAWN_COLOR.b) * t
    };
}

Engine.onTick(function (tpf, tag) {
    var degPerSecond = 360 / rotationPeriodSeconds;
    angleDeg = (angleDeg + degPerSecond * tpf) % 360;

    var rad = angleDeg * Math.PI / 180;
    var p = Player.getPosition();

    var x = p[0] + Math.cos(rad) * ORBIT_RADIUS;
    var y = p[1] + Math.sin(rad) * ORBIT_RADIUS;
    var z = p[2];

    Scene.setPosition(sunCube, x, y, z);
    Scene.setPosition(sunLight, x, y, z);

    var c = colorForAngle(angleDeg);
    Scene.setColor(sunCube, c.r, c.g, c.b, 1.0);
    Scene.setLightColor(sunLight, c.r, c.g, c.b);
}, "Update");

// ── GUI: rotation-period selector ───────────────────────────────────────
function periodLabel(opt) {
    return opt.label + (opt.seconds === rotationPeriodSeconds ? " [selected]" : "");
}

function drawPeriodButtons() {
    for (var i = 0; i < PERIOD_OPTIONS.length; i++) {
        var opt = PERIOD_OPTIONS[i];
        var handle = Gui.guiWord(periodLabel(opt), 0.85, 0.75 - i * 0.04, 0, 0.02, "sunPeriod" + i);
        Gui.setColor(handle,
            opt.seconds === rotationPeriodSeconds ? 0.2 : 0.9,
            opt.seconds === rotationPeriodSeconds ? 1.0 : 0.9,
            opt.seconds === rotationPeriodSeconds ? 0.2 : 0.9,
            1.0);
    }
}

drawPeriodButtons();

(function registerPeriodHandlers() {
    for (var i = 0; i < PERIOD_OPTIONS.length; i++) {
        (function (index) {
            Engine.onTick(function (tpf, tag) {
                rotationPeriodSeconds = PERIOD_OPTIONS[index].seconds;
                drawPeriodButtons(); // re-upsert all labels/colors so the new selection highlights
            }, "sunPeriod" + index);
        })(i);
    }
})();
