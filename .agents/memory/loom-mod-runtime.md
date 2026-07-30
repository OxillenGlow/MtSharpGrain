---
name: Virtual-thread mod runtime
description: Thread-ownership rules for the per-pack GraalJS runtime and engine marshaling.
---

Each mod pack owns one GraalJS context on one virtual thread. Java callers must submit
Graal work through that pack's mailbox; engine/jME state must be accessed through the
render-thread gateway, whose future wait is allowed only on the mod virtual thread.
Inter-mod messages are FIFO mailbox work; messages arriving before startup completes
are buffered and delivered after scripts load.

**Why:** GraalJS contexts are not thread-safe, while mod scripts need synchronous-looking
engine queries without blocking the render loop.

**How to apply:** Keep new JS callbacks and event dispatches on the pack mailbox. Keep
world mutations on the render thread after asynchronous validation completes; never call
into a pack context directly from Main, input listeners, or another pack. Validation
requests remain pending until all validators finish; add an explicit deadline only if
the product wants hung validators to fail closed.