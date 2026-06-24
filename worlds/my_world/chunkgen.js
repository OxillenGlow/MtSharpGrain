// ============================================================================
// chunkgen.js
// chunkBuild(x, y, z, seed) — called once per chunk by JsChunkGenerator.
// x, y, z are CHUNK coordinates (multiply by 16 for world-space block coords).
//
// Pipeline, in order:
//   1. hash2()              — deterministic pure-function randomness primitive
//   2. baseHeight()         — rolling sine/cosine heightmap
//   3. terrainFeatureDelta()— sparse mountains (cones) + slashes (capsule pits)
//   4. iceThickness()       — polar ice cap layered on top of rock height
//   5. subsurfaceBlock()    — dirt/rock gradient for the top few layers
//   6. pickUndergroundBlock()— ore/rock below the dirt gradient
//   7. per-column surface decoration (stray dirt/rock nubs)
//   8. chunk-level template swap (storageAir / storageGround)
// ============================================================================


// ── 1. Deterministic hash ───────────────────────────────────────────────────
// Pure function of (ix, iz, seed) -> [0, 1). No state, no Math.random().
// This is the single randomness primitive every other function below uses,
// so generation stays 100% order-independent across chunks and re-runs.
function hash2(ix, iz, seed) {
    var h = (ix * 374761393 + iz * 668265263 + seed * 982451653) | 0;
    h = (h ^ (h >>> 13)) * 1274126177;
    h = h ^ (h >>> 16);
    return ((h >>> 0) % 2147483647) / 2147483647;
}


// ── 2. Base heightmap ───────────────────────────────────────────────────────
// Three stacked sine/cosine waves at different frequencies/amplitudes.
// No noise function — intentionally simple and easy to retune by hand.
function baseHeight(wx, wz, seed) {
    var h = 0;
    h += 6   * Math.sin(wx * 0.013 + seed)        * Math.cos(wz * 0.011 - seed);
    h += 3   * Math.sin(wx * 0.041 - seed * 0.7)  * Math.cos(wz * 0.037 + seed * 0.7);
    h += 1.2 * Math.sin(wx * 0.097 + seed * 1.3)  * Math.cos(wz * 0.083 - seed * 1.3);
    return h; // roughly ±10 — rolling Mars dunes
}


// ── 3. Sparse mountains & slashes (coarse cell-hash grid) ──────────────────
var CELL_SIZE = 600;
var MAX_REACH = 600; // must be >= half the largest feature dimension below

// Returns a feature descriptor for this cell, or null if the cell is empty.
function featureAt(cellX, cellZ, seed) {
    if (hash2(cellX, cellZ, seed) > 0.08) return null; // ~8% of cells spawn something

    var cx = cellX * CELL_SIZE + hash2(cellX * 7 + 1, cellZ * 13 + 2, seed) * CELL_SIZE;
    var cz = cellZ * CELL_SIZE + hash2(cellX * 17 + 3, cellZ * 23 + 5, seed) * CELL_SIZE;
    var isMountain = hash2(cellX * 31 + 7, cellZ * 29 + 11, seed) < 0.5;

    if (isMountain) {
        return {
            type: "mountain",
            cx: cx, cz: cz,
            radius: 40 + hash2(cellX * 3, cellZ * 5, seed) * 110,
            peak:   20 + hash2(cellX * 5, cellZ * 7, seed) * 60
        };
    }

    return {
        type: "slash",
        cx: cx, cz: cz,
        angle:  hash2(cellX * 9, cellZ * 19, seed) * Math.PI * 2,
        length: 200 + hash2(cellX * 11, cellZ * 15, seed) * 800,
        width:  20  + hash2(cellX * 21, cellZ * 25, seed) * 40,
        depth:  15  + hash2(cellX * 27, cellZ * 33, seed) * 35
    };
}

// Sums the height delta from every nearby feature that could overlap (wx, wz).
function terrainFeatureDelta(wx, wz, seed) {
    var delta = 0;
    var x0 = Math.floor((wx - MAX_REACH) / CELL_SIZE), x1 = Math.floor((wx + MAX_REACH) / CELL_SIZE);
    var z0 = Math.floor((wz - MAX_REACH) / CELL_SIZE), z1 = Math.floor((wz + MAX_REACH) / CELL_SIZE);

    for (var cx = x0; cx <= x1; cx++) {
        for (var cz = z0; cz <= z1; cz++) {
            var f = featureAt(cx, cz, seed);
            if (!f) continue;

            var dx = wx - f.cx, dz = wz - f.cz;

            if (f.type === "mountain") {
                var dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < f.radius) delta += f.peak * (1 - dist / f.radius);
                continue;
            }

            // slash: rotate (dx, dz) into the feature's local (length, width) frame
            var ca = Math.cos(-f.angle), sa = Math.sin(-f.angle);
            var u = dx * ca - dz * sa;
            var v = dx * sa + dz * ca;
            var halfLen = f.length / 2, halfW = f.width / 2;

            if (Math.abs(v) >= halfW || u <= -halfLen || u >= halfLen) continue;

            var endFalloff = 1;
            if (u < -halfLen + halfW)      endFalloff = 1 - Math.min(1, (-halfLen + halfW - u) / halfW);
            else if (u > halfLen - halfW)  endFalloff = 1 - Math.min(1, (u - (halfLen - halfW)) / halfW);

            var sideFalloff = Math.max(0.85, 1 - Math.abs(v) / halfW); // mostly flat floor
            delta -= f.depth * endFalloff * sideFalloff;
        }
    }
    return delta;
}


// ── 4. Polar ice cap ─────────────────────────────────────────────────────────
// Linear cone centered at world origin: full thickness at r=0, zero at r=ICE_MAX_RADIUS.
var ICE_MAX_RADIUS = 100000;
var ICE_MAX_THICKNESS = 30;

function iceThickness(wx, wz) {
    var r = Math.sqrt(wx * wx + wz * wz);
    if (r >= ICE_MAX_RADIUS) return 0;
    return ICE_MAX_THICKNESS * (1 - r / ICE_MAX_RADIUS);
}


// ── 5. Dirt/rock gradient for the top few layers ───────────────────────────
// depthIndex: 0 = topmost rock/dirt layer (just under the surface), increasing downward.
var DIRT_CHANCE_BY_DEPTH = [0.99, 0.80, 0.60, 0.10];

function subsurfaceBlock(wx, wy, wz, seed, depthIndex) {
    var r = hash2(wx * 53 + wy * 191, wz * 97 + seed * 331, seed);
    return r < DIRT_CHANCE_BY_DEPTH[depthIndex] ? 3 /* dirt */ : 2 /* rock */;
}


// ── 6. Deep underground material (rock / ore) ───────────────────────────────
function pickUndergroundBlock(wx, wy, wz, seed, depthBelowSurface) {
    var r = hash2(wx * 131 + wz * 977, wy * 733 + seed * 101, seed);
    var oreChance = Math.min(0.10, 0.01 + depthBelowSurface * 0.0006);
    if (r < oreChance) return 5; // Crystal Ore
    return 2; // Stone
}


// ── 7 & 8 combined in the main entry point ──────────────────────────────────
function chunkBuild(x, y, z, seed) {
    var worldX = x * 16, worldY = y * 16, worldZ = z * 16;
    var flat = new Array(4096);

    var minSurfaceTop = Infinity;
    var maxRockTop = -Infinity;

    for (var lx = 0; lx < 16; lx++) {
        for (var lz = 0; lz < 16; lz++) {
            var wx = worldX + lx;
            var wz = worldZ + lz;

            var rockTop = 10 + baseHeight(wx, wz, seed) + terrainFeatureDelta(wx, wz, seed);
            var ice = iceThickness(wx, wz);
            var surfaceTop = rockTop + ice;

            minSurfaceTop = Math.min(minSurfaceTop, surfaceTop);
            maxRockTop = Math.max(maxRockTop, rockTop);

            // Surface decoration roll — computed once per column, not per block.
            var deco = hash2(wx * 811 + seed, wz * 409 - seed, seed * 7);
            var decoBlock = 0;
            if (deco < 0.02)      decoBlock = 3; // 2% stray dirt nub
            else if (deco < 0.03) decoBlock = 2; // 1% stray rock nub

            for (var ly = 0; ly < 16; ly++) {
                var wy = worldY + ly;
                var block;
                var depthFromSurface = rockTop - 1 - wy; // 0 = topmost solid layer

                if (wy >= surfaceTop) {
                    // Only the single air cell directly above the surface can hold a decoration.
                    block = (wy === surfaceTop) ? decoBlock : 0;
                } else if (wy >= rockTop) {
                    block = 6; // ice
                } else if (depthFromSurface >= 0 && depthFromSurface < 4) {
                    block = subsurfaceBlock(wx, wy, wz, seed, depthFromSurface);
                } else {
                    block = pickUndergroundBlock(wx, wy, wz, seed, rockTop - wy);
                }

                flat[lx * 256 + ly * 16 + lz] = block;
            }
        }
    }

    // ── Chunk-level template swap ────────────────────────────────────────
    // A chunk is "all air" if even its lowest-surface column sits above the chunk's top.
    var isAllAir = minSurfaceTop <= worldY;
    // A chunk is "all underground" if even its highest rock surface sits below the chunk's bottom.
    var isAllUnderground = maxRockTop >= worldY + 16;

    var swapRoll = hash2(x * 41 + 3, z * 43 + 5, seed * 13 + y * 17);

    if (isAllAir && swapRoll < 0.005) {
        var airFile = Chunk.pickFile("storageAir", hash2(x * 51, z * 53, seed * 19));
        if (airFile) {
            var loaded = Chunk.load(airFile);
            if (loaded) flat = loaded;
        }
    } else if (isAllUnderground && swapRoll < 0.10) {
        var groundFile = Chunk.pickFile("storageGround", hash2(x * 61, z * 63, seed * 23));
        if (groundFile) {
            var loaded2 = Chunk.load(groundFile);
            if (loaded2) flat = loaded2;
        }
    }

    Chunk.setArray(flat);
}
