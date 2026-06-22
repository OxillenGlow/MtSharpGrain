// chunkBuild(x, y, z, seed)
// x, y, z are CHUNK coordinates. Multiply by 16 for world-space block coords.
function chunkBuild(x, y, z, seed) {
    var worldX = x * 16;
    var worldY = y * 16;
    var worldZ = z * 16;

    for (var lx = 0; lx < 16; lx++) {
        for (var lz = 0; lz < 16; lz++) {
            var wx = worldX + lx;
            var wz = worldZ + lz;
            // placeholder height field — swap for real noise later
            var height = Math.floor(8 + 4 * Math.sin(wx * 0.1 + seed) * Math.cos(wz * 0.1 + seed));

            for (var ly = 0; ly < 16; ly++) {
                var wy = worldY + ly;
                Chunk.set(lx, ly, lz, wy < height ? 1 : 0); // 1 = stone, 0 = air
            }
        }
    }
}
