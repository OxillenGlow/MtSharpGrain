// Demo: every tick, sets the block 1 meter above the player to Glass (id 10).
Engine.onTick(function(tpf, tag) {
    const p = Player.getPosition();
    const x = Math.floor(p[0]);
    const y = Math.floor(p[1]) - 1;
    const z = Math.floor(p[2]);
    Block.place(x, y, z, 10);
}, "Update");
