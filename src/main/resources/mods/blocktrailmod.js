// Toggle-able block trail: places Glass (id 10) one block below the player each tick.
// Click the "Block Trail" label to turn it on/off.

let trailEnabled = false;

function trailLabel() {
    return "Block Trail: " + (trailEnabled ? "ON" : "OFF");
}

// Draws (and re-draws on toggle) the clickable label. GuiApi.guiWord upserts
// by tag, so calling it again with the same tag just updates this element.
Gui.guiWord(trailLabel(), 0.5, 0.2, 0, 0.03, "toggleBlockTrail");

// Fires when the label above is clicked (JSModifier.processGuiClicks dispatches
// tickTag(tpf, tag) for whichever tags got clicked this frame).
Engine.onTick(function(tpf, tag) {
    trailEnabled = !trailEnabled;
    Gui.guiWord(trailLabel(), 0.5, 0.2, 0, 0.03, "toggleBlockTrail");
}, "toggleBlockTrail");

// The actual trail effect — runs every frame under the "Update" tag,
// gated by trailEnabled instead of being commented out.
Engine.onTick(function(tpf, tag) {
    if (!trailEnabled) return;
    const p = Player.getPosition();
    const x = Math.floor(p[0]);
    const y = Math.floor(p[1]) - 1;
    const z = Math.floor(p[2]);
    Block.forceSet(x, y, z, 10);
}, "Update");
