const cube = Scene.createCube("myCube", 1.0);
Scene.attachChild(0, cube);
Scene.setPosition(cube, 0, 10, 0);

Engine.onTick(function(tpf, tag) {
    const p = Scene.getPosition(cube);
    const blockBelow = Scene.getBlockId(Math.floor(p[0]), Math.floor(p[1]) - 1, Math.floor(p[2]));
    if (blockBelow !== 0) {
        Scene.setColor(cube, 0, 1, 0, 1); // green when over solid ground
    }
}, "Update");
