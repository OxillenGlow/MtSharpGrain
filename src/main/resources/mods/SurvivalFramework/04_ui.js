// ============================================================================
// 04_ui.js — Deadly-Inventory
//
// Vertical paged inventory list on the mod's own GuiApi canvas — same
// page-browsing idea as Master.drawBlockTypeSelector, just top-to-bottom
// instead of a horizontal row. Clicking a row sets `selectedBlock` (defined
// in 02_inventory.js), which the Engine.onBlockChange gate spends from when
// placing.
// ============================================================================

var INV_ROWS_PER_PAGE = 10;
var INV_ROW_SPACING = 0.06;
var INV_TOP_Y = 0.90;

var invPageStart = 0; // ids shown this page: invPageStart .. invPageStart + INV_ROWS_PER_PAGE - 1

function drawInventory() {
    for (var i = 0; i < INV_ROWS_PER_PAGE; i++) {
        var id = invPageStart + i;
        var count = getInvCount(id);
        var label = "[" + id + "] " + blockName(id) + "  x" + count;
        var y = INV_TOP_Y - i * INV_ROW_SPACING;

        var handle = Gui.guiWord(label, 0.5, y, 0, 0.03, "inventory" + i);

        if (id === selectedBlock) {
            Gui.setColor(handle, 0.2, 1.0, 0.2, 1.0); // green = currently selected
        } else if (count > 0) {
            Gui.setColor(handle, 0.9, 0.9, 0.9, 1.0); // white = have some
        } else {
            Gui.setColor(handle, 0.5, 0.5, 0.5, 1.0); // grey = empty
        }
    }

    var upHandle = Gui.guiWord("^ Prev Page", 0.5, INV_TOP_Y + INV_ROW_SPACING, 0, 0.025, "invPagePrev");
    Gui.setColor(upHandle, 0.4, 0.6, 1.0, 1.0);

    var downHandle = Gui.guiWord("v Next Page", 0.5, INV_TOP_Y - INV_ROWS_PER_PAGE * INV_ROW_SPACING, 0, 0.025, "invPageNext");
    Gui.setColor(downHandle, 0.4, 0.6, 1.0, 1.0);
}

// Refresh labels/colors every frame so counts stay live.
Engine.onTick(function (tpf, tag) {
    drawInventory();
}, "Update");

Engine.onTick(function (tpf, tag) {
    invPageStart -= INV_ROWS_PER_PAGE;
}, "invPagePrev");

Engine.onTick(function (tpf, tag) {
    invPageStart += INV_ROWS_PER_PAGE;
}, "invPageNext");

// One click handler per row SLOT on the page (not per block id — the id it
// selects is whatever happens to be showing in that slot at click time).
(function registerRowHandlers() {
    for (var i = 0; i < INV_ROWS_PER_PAGE; i++) {
        (function (rowIndex) {
            Engine.onTick(function (tpf, tag) {
                selectedBlock = invPageStart + rowIndex;
            }, "invRow" + rowIndex);
        })(i);
    }
})();
