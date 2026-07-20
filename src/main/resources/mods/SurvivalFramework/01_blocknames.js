// ============================================================================
// 01_blocknames.js — Deadly-Inventory
//
// Human-readable names for inventory rows. Extend either array by just
// appending ', "Name"' to the end:
//   - BLOCK_NAMES_POS: index == block id  (id 0 -> "Air", id 2 -> "Stone", ...)
//   - BLOCK_NAMES_NEG: index 0 == id -1, index 1 == id -2, ... (reserved for
//     future non-block "item" ids — tools, currency, whatever — kept negative
//     so they can never collide with a real BlockRegistry id)
// Anything past the end of either array just falls back to showing its raw id.
// ============================================================================

var BLOCK_NAMES_POS = [
    "Air", "Reserved", "Stone", "Dirt", "Grass",
    "Crystal Ore", "Ice Sludge", "Silicon", "Sulfur", "Metal Block", "Glass"
];

var BLOCK_NAMES_NEG = [
    // "Repair Kit", "Teleport Charge"
];

function blockName(id) {
    if (id >= 0) {
        return id < BLOCK_NAMES_POS.length ? BLOCK_NAMES_POS[id] : ("Block " + id);
    }
    var idx = (-id) - 1;
    return idx < BLOCK_NAMES_NEG.length ? BLOCK_NAMES_NEG[idx] : ("Item " + id);
}
