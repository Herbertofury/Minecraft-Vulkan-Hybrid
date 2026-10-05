# Isolated native benchmark control

Build with Java 17 using `source/gradlew -p benchmarks/control-forge build`. This Forge 1.20.1 recorder fixes the camera, warms for 60 seconds, captures every RenderTick END wall interval for 30 seconds, records the actual native Vulkan selector or GL driver strings without opening a GL context during native Vulkan rendering, saves two screenshots, then exits the game normally. The interval data include hitches; percentiles and the worst 1% average retain all samples. These are CPU-side frame intervals rather than GPU timestamp or presentation measurements.

**Install only in a disposable benchmark profile/world.** The controller moves the player and automatically stops the game; it is never part of the user's playable pack. The launcher verifies task-owned IDs and profile markers before use. Smoke mode additionally requires `-Dmvh.pack.smoke=true` and the task's `noxviola-candidate` game directory. It reloads resources, spawns/kills a modded octopus, visits the Nether and returns to the Overworld. Those actions invalidate any FPS comparison from a smoke run.

The matched runner restores the same checksum-verified world before each trial, retains old worlds/screenshots recoverably, applies identical visual options, records owned Java CPU/RSS and whole-adapter GPU load, and uses ABBAAB ordering. Concurrent projects stay active. Original pack JARs and private complete logs are not redistributed.

Recorder 1.0.5 requires an isolated-profile marker and rejects the live Noxviola directory. Native Vulkan metadata is collected without direct LWJGL GL references, so installing the recorder does not itself cause the renderer compatibility gate to select OpenGL. The held item registry ID is recorded to expose scene changes from auto-given mod books.

Before joining a world, recorder 1.0.5 writes a small loading-state heartbeat every five seconds. It stops those writes when the scene begins, keeping benchmark timing free of heartbeat I/O. The diagnostic identifies blocking loading screens; it never accepts loading errors automatically. A baseline without Hari records its actual OpenGL driver.
