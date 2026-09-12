// Confetti Burst — a harmless "fun" mod. Click the button to pop a burst of
// colorful floating cubes around you for a couple seconds. Purely visual —
// uses Scene (not Block), so nothing is written to the world/save file.
globalThis.Confetti = {
    active: [],
    root: 0, // handle 0 = world root, fixed-registered by JSModifier.init()
    animId: null,
};

Gui.guiWord("Pop Confetti!", 0.8, 0.35, 0, 0.03, "confetti_pop");

// Spawn burst when the GUI button is clicked (still driven by the click tag).
Engine.onTick(function(tpf, tag) {
    var p = Player.getPosition();
    var count = 24;
    for (var i = 0; i < count; i++) {
        var handle = Scene.createCube("confetti_" + Date.now() + "_" + i, 0.3);
        Scene.attachChild(Confetti.root, handle);
        Scene.setPosition(handle, p[0], p[1] + 1.5, p[2]);
        Scene.setColor(handle, Math.random(), Math.random(), Math.random(), 1.0);
        var angle = Math.random() * Math.PI * 2;
        var speed = 2 + Math.random() * 3;
        Confetti.active.push({
            handle: handle,
            vx: Math.cos(angle) * speed,
            vy: 4 + Math.random() * 3,
            vz: Math.sin(angle) * speed,
            x: p[0], y: p[1] + 1.5, z: p[2],
            life: 2.0
        });
    }

    // Start the animation loop if it isn't already running.
    if (Confetti.animId === null) {
        // ~60 fps: fixed step of 1/60 s (setInterval does not pass tpf).
        var step = 1 / 60;
        Confetti.animId = Engine.setInterval(function() {
            var gravity = 9.0;
            for (var i = Confetti.active.length - 1; i >= 0; i--) {
                var c = Confetti.active[i];
                c.vy -= gravity * step;
                c.x += c.vx * step;
                c.y += c.vy * step;
                c.z += c.vz * step;
                c.life -= step;
                if (c.life <= 0) {
                    Scene.destroy(c.handle);
                    Confetti.active.splice(i, 1);
                    continue;
                }
                Scene.setPosition(c.handle, c.x, c.y, c.z);
            }

            // Stop the timer when nothing is left to animate.
            if (Confetti.active.length === 0) {
                Engine.clearInterval(Confetti.animId);
                Confetti.animId = null;
            }
        }, 100);
    }
}, "confetti_pop");