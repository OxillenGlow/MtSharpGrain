// DelayedAction - Tests Engine.setTimeout
// Click "Spawn Cube in 3s" to spawn a cube after a 3-second delay.
// Shows a countdown in the GUI.

globalThis.DelayedAction = {
    countdown: 0,
    timeoutId: null,
    countdownHandle: null
};

// --- Setup GUI ---
Gui.guiWord("Delayed Action Demo", 0.5, 0.4, 0, 0.03, "delayed_title");
Gui.guiWord("Countdown: 0s", 0.5, 0.35, 0, 0.025, "delayed_countdown");
Gui.guiWord("Spawn Cube in 3s", 0.5, 0.3, 0, 0.025, "delayed_spawn");

// --- Click Handler ---
// Start a 3-second countdown, then spawn a cube
Engine.onTick(function(tpf, tag) {
    // Clear any existing timeout
    if (DelayedAction.timeoutId !== null) {
        Engine.clearTimeout(DelayedAction.timeoutId);
    }

    // Reset countdown display
    DelayedAction.countdown = 3;
    Gui.guiWord("Countdown: " + DelayedAction.countdown + "s", 0.5, 0.35, 0, 0.025, "delayed_countdown");

    // Start countdown updates (every 1s)
    const countdownInterval = Engine.setInterval(function() {
        DelayedAction.countdown--;
        Gui.guiWord("Countdown: " + DelayedAction.countdown + "s", 0.5, 0.35, 0, 0.025, "delayed_countdown");
    }, 1000);

    // After 3 seconds, spawn a cube and clean up
    DelayedAction.timeoutId = Engine.setTimeout(function() {
        Engine.clearInterval(countdownInterval); // Stop countdown
        Gui.guiWord("Countdown: Done!", 0.5, 0.35, 0, 0.025, "delayed_countdown");

        // Spawn a cube at the player's position
        const p = Player.getPosition();
        const cube = Scene.createCube("delayed_cube_" + Date.now(), 1.0);
        Scene.attachChild(0, cube);
        Scene.setPosition(cube, p[0], p[1] + 2, p[2]);
        Scene.setColor(cube, 0, 1, 0, 1); // Green cube
    }, 3000);
}, "delayed_spawn");
