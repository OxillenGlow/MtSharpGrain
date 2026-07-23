// ============================================================================
// 02_inventory.js — Deadly-Inventory
//
// Per-id inventory counts, persisted as one JSON object via Data.save/Data.get
// (lands at worlds/<world>/mod/Deadly-Inventory/data/inventory.xml — the XML
// wrapper is DataApi's own format, the payload inside it is our JSON):
//
//   inventory = { "<id>": count, ... }   // both positive and negative ids
//
// Also owns the place/break economy: Engine.onBlockChange runs on every
// world edit and decides, from what's already at (x, y, z), whether this is
// a break (give material back) or a place (spend material).
// ============================================================================

var INVENTORY_SAVE_LOCATION = "inventory";
var inventory = {};

function loadInventory() {
    var raw = Data.get(INVENTORY_SAVE_LOCATION);
    if (!raw) {
        inventory = {};
        return;
    }
    try {
        inventory = JSON.parse(raw);
    } catch (e) {
        inventory = {};
    }
}

function saveInventory() {
    Data.save(JSON.stringify(inventory), INVENTORY_SAVE_LOCATION);
}

function getInvCount(id) {
    var v = inventory[id];
    return v ? v : 0;
}

function setInvCount(id, amount) {
    inventory[id] = amount;
    saveInventory();
}

/** amount may be negative to spend; count is clamped at 0. */
function addInv(id, amount) {
    var v = getInvCount(id) + amount;
    if (v < 0) v = 0;
    inventory[id] = v;
    saveInventory();
}

loadInventory();

// Currently selected block for placement — mod-local, driven by the paged
// inventory UI in 04_ui.js, independent of the core home-screen selector.
var selectedBlock = 2; // Stone, sane default

// ── Place / break gate ──────────────────────────────────────────────────
// Block.forcePlace() below goes through WorldAccess.forceSetBlockAt(), which
// does NOT call modPackManager.notifyBlockSet — so it can't recurse back
// into this validator. Block.place() would have (setBlockAt -> notifyBlockSet
// -> this same onBlockChange callback), which is why forcePlace exists.
Engine.onBlockChange(function (x, y, z, blockId) {
    var existing = Block.get(x, y, z);

    if (existing !== 0 && existing !== 1) {
        // Something solid is already there -> this is a break, not a place.
        addInv(existing, 1);
        return true; // let the normal break proceed
    }

    // Target cell is air/reserved -> this is a place attempt.
    var have = getInvCount(selectedBlock);
    if (have >= 1) {
        Block.forceSet(x, y, z, selectedBlock);
        addInv(selectedBlock, -1);
    }
    // Either we already placed it ourselves, or we're refusing for lack of
    // material — reject the original request either way so the caller's
    // raw blockId never gets applied on top of what we just did.
    return false;
});
