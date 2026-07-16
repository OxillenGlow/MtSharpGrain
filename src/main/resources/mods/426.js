// GeoHasher — inspired by xkcd 426 (https://xkcd.com/426/). On load, picks a
// random-but-deterministic-for-today destination within 10 km of wherever
// you were standing when the world loaded, and challenges you to walk there.

globalThis.GeoHasher = {
    origin: null,
    destination: null,
    reachedRadius: 5,   // blocks — how close counts as "there"
    maxDistance: 10000, // 10 km cap, in blocks (assumes ~1 block == 1 meter)
};

// Same deterministic hash pattern used in chunkgen.js — pure function of its
// inputs, so "today's" destination is stable across restarts on the same day.
function geoHash(a, b, seed) {
    var h = (a * 374761393 + b * 668265263 + seed * 982451653) | 0;
    h = (h ^ (h >>> 13)) * 1274126177;
    h = h ^ (h >>> 16);
    return ((h >>> 0) % 2147483647) / 2147483647;
}

(function initGeoHasher() {
    var p = Player.getPosition();
    GeoHasher.origin = [Math.floor(p[0]), Math.floor(p[1]), Math.floor(p[2])];

    var today = new Date();
    var daySeed = today.getFullYear() * 372 + today.getMonth() * 31 + today.getDate();

    var angle = geoHash(GeoHasher.origin[0], GeoHasher.origin[2], daySeed) * Math.PI * 2;
    var distance = geoHash(daySeed, GeoHasher.origin[0] - GeoHasher.origin[2], daySeed * 7) * GeoHasher.maxDistance;

    var dx = Math.round(Math.cos(angle) * distance);
    var dz = Math.round(Math.sin(angle) * distance);

    GeoHasher.destination = [
        GeoHasher.origin[0] + dx,
        GeoHasher.origin[1], // no terrain-height query available, so this is approximate — expect climbing/digging
        GeoHasher.origin[2] + dz
    ];

    Gui.guiWord(
        "Today's spot: " + GeoHasher.destination[0] + ", " + GeoHasher.destination[2]
        + "  (" + Math.round(distance) + "m away) /n Don't know how to play? search 'Geohasher' online",
        0.5, 0.55, 0, 0.022, "geo_target"
    );
    Gui.guiWord("I'm there!", 0.5, 0.5, 0, 0.03, "geo_check");
})();

Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    var dx = p[0] - GeoHasher.destination[0];
    var dz = p[2] - GeoHasher.destination[2];
    var dist = Math.sqrt(dx * dx + dz * dz);

    Gui.guiWord(
        dist <= GeoHasher.reachedRadius
            ? "You made it! (within " + Math.round(dist) + "m)"
            : "Not quite — " + Math.round(dist) + "m to go. Keep walking!",
        0.5, 0.5, 0, 0.03, "geo_check"
    );
}, "geo_check");
