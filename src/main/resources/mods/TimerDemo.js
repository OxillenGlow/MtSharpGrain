// TimerDemo - Tests Engine.setInterval and Engine.clearInterval
// Creates a counter that increments every second.
// Click "Start Timer" to begin, "Stop Timer" to pause.

globalThis.TimerDemo = {
    counter: 0,
    intervalId: null,
    counterHandle: null,
    startBtnHandle: null,
    stopBtnHandle: null
};

// --- Setup GUI ---
Gui.guiWord("Timer Demo", 0.5, 0.7, 0, 0.03, "timer_title");
Gui.guiWord("Counter: 0", 0.5, 0.65, 0, 0.025, "timer_counter");
Gui.guiWord("Start Timer", 0.4, 0.55, 0, 0.025, "timer_start");
Gui.guiWord("Stop Timer", 0.6, 0.55, 0, 0.025, "timer_stop");

// --- Click Handlers ---
// Start the timer (setInterval)
Engine.onTick(function(tpf, tag) {
    // Clear any existing timer
    if (TimerDemo.intervalId !== null) {
        Engine.clearInterval(TimerDemo.intervalId);
    }
    // Reset counter
    TimerDemo.counter = 0;
    Gui.guiWord("Counter: " + TimerDemo.counter, 0.5, 0.65, 0, 0.025, "timer_counter");

    // Start a new interval (1000ms = 1 second)
    TimerDemo.intervalId = Engine.setInterval(function() {
        TimerDemo.counter++;
        Gui.guiWord("Counter: " + TimerDemo.counter, 0.5, 0.65, 0, 0.025, "timer_counter");
    }, 1000);
}, "timer_start");

// Stop the timer (clearInterval)
Engine.onTick(function(tpf, tag) {
    if (TimerDemo.intervalId !== null) {
        Engine.clearInterval(TimerDemo.intervalId);
        TimerDemo.intervalId = null;
    }
}, "timer_stop");
