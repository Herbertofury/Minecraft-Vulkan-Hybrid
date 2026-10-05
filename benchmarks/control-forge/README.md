# Isolated native benchmark control

Build with Java 17 using `source/gradlew -p benchmarks/control-forge build`. This Forge 1.20.1 recorder fixes the camera, warms for 60 seconds, captures every RenderTick END wall interval for 30 seconds, records the actual GL driver strings, saves two screenshots, then exits the game normally. The interval data include hitches; percentiles and the worst 1% average retain all samples. These are CPU-side frame intervals rather than GPU timestamp or presentation measurements.

**Install only in a disposable benchmark profile/world.** The controller moves the player and automatically stops the game; it is never part of the user's playable pack. The launcher verifies task-owned IDs and profile markers before use. Smoke mode additionally requires `-Dmvh.pack.smoke=true` and the task's `noxviola-candidate` game directory. It reloads resources, spawns/kills a modded octopus, visits the Nether and returns to the Overworld. Those actions invalidate any FPS comparison from a smoke run.

The matched runner restores the same checksum-verified world before each trial, retains old worlds/screenshots recoverably, applies identical visual options, records owned Java CPU/RSS and whole-adapter GPU load, and uses ABBAAB ordering. Concurrent projects stay active. Original pack JARs and private complete logs are not redistributed.
