# HariMultiThread Ultimate 2.1.0-noxviola.1

Forge 1.20.1 release-hardened continuation of HariMultiThread / Async.

## Release target

- Minecraft 1.20.1
- Forge 47.4.23
- Java 17
- Upstream base: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
- License: GPL-3.0-only
- Mod id retained: `harimt`

## What changed

### Async entity correctness and throughput

- HMT v2 work stealing, affinity routing, per-dimension locks, dynamic workers, telemetry and circuit breaker retained.
- One logical CPU is reserved by default instead of saturating every processor.
- Ender Dragon is forced synchronous, matching later Async correctness work.
- EntitySection queries use locked snapshots rather than unsafe concurrent iteration.
- Tracked-entity removal/update paths are serialized to avoid unremovable client ghost entities.
- Worker/GPU/deferred state is reset cleanly on server stop and integrated-server restart.

### Spawn hardening based on the latest Async lineage

- Async spawn remains enabled; it was not simply disabled for safety.
- Fire-and-forget spawn work is replaced with same-tick managed batches and a barrier.
- Global `parallelStream()` spawn-state construction is removed.
- Vanilla Forge 1.20.1 `SpawnState` mutation remains authoritative and is synchronized instead of replacing vanilla collection identities.
- Each queued spawn task closes over the exact `LevelChunk` that vanilla already supplied to `tickChunks`, then invokes `NaturalSpawner.spawnForChunk(level, chunk, ...)` behind the same-tick HMT barrier. The final Forge 1.20.1 path uses no worker-side chunk relookup, `ThreadLocal` spawn context, or scheduled-chunk shim mixin.
- Optional async random ticks are also same-tick/barriered instead of leaking work into later ticks.

### C2ME / DimThread interop

- Both `c2me` and `c2meforge` IDs are recognized.
- HMT waits pump only the current dimension's chunk queue rather than draining every loaded dimension from one worker.
- The design matches the Noxviola SAFE-INTEROP DimThread contract, where a dimension worker temporarily owns that dimension's logical `ServerLevel` / `ServerChunkCache.mainThread` identity and restores it under a synchronized `finally` boundary.

### Save safety

- Forge 1.20.1 `PalettedContainer` reads/writes/saves/copies/scans are guarded by a read/write lock to prevent async palette corruption during chunk serialization.

## Real Vulkan collision acceleration

The original HMT Vulkan layer was not release-ready: candidate pairs were not consumed by Minecraft collision work, reflection mixed raw handles with LWJGL wrapper APIs, the shader/pipeline ABI disagreed, SPIR-V was not guaranteed to be packaged, repeated command-buffer recording lacked a reset, overflow could truncate results, and a partial final workgroup could diverge around barriers.

This release replaces that path instead of layering more reflection on top of it.

- A tiny parent-side `VulkanCollisionBackend` interface uses only Java primitive arrays/results.
- The actual LWJGL 3.3.1 Vulkan implementation is javac-compiled in a dedicated pure-Java source set and embedded as an inert child JAR.
- Forge/ModLauncher never sees `lwjgl-vulkan` as a module dependency.
- On Minecraft clients the parent-first child loader reuses Minecraft's LWJGL core/native runtime; on dedicated servers it can supply embedded core/native JARs.
- Hardware Vulkan devices are preferred automatically; software/CPU Vulkan is opt-in and is enabled only in CI to exercise the compute path under Mesa.
- GPU input is the entities' actual runtime AABBs, outward-rounded during float conversion so precision narrowing can create conservative extras but cannot remove a true overlap.
- The shader uses nine SSBO bindings, 16-byte push constants and uniform workgroup barriers even in a partial final workgroup.
- Every dispatch resets/re-records the command buffer, submits behind a fence and has a bounded timeout.
- Output overflow, device loss, invalid indices, initialization failure or any incomplete result causes a complete vanilla fallback; collision physics are never silently truncated.

### Stable post-barrier push replay

HMT can tick synchronous fallback entities while async entity workers are still running. To keep collision/crowding from observing in-flight positions, the release makes deferred push a **world-batch invariant**:

1. HMT marks the dimension's entity batch active before either synchronous or async entity ticks begin.
2. Every `LivingEntity.pushEntities()` invocation in that active batch is queued, regardless of whether that particular entity was synchronous or asynchronous.
3. HMT waits for all workers.
4. The original vanilla `pushEntities()` calls are replayed on the stable dimension/server thread in original call count order.
5. Vulkan may replace only the exact broad-phase entity lookup with a conservative candidate set.
6. Vanilla predicates, double-precision AABB intersection, cramming rules, Forge hooks and `doPush` remain authoritative.
7. If GPU acceleration is unavailable, the same deferred vanilla calls replay with normal vanilla queries.

## Operator QoL

- `/async gpu` reports the real backend/device, dispatch count, last dispatch time, deferred GPU batches, vanilla fallbacks, pair count and circuit-breaker state.
- `/async gpu toggle` switches acceleration live while preserving vanilla replay.
- `/async gpu test` is no longer a synthetic CPU benchmark mislabeled as GPU. It compares live-world Vulkan candidates against an exact double-precision CPU AABB reference for up to 512 entities.
- Conservative GPU extras are allowed; a single missing true pair is a correctness failure and automatically disables GPU acceleration + persists the setting.

## Release gates

The release workflow must pass all of these before GitHub publication:

- deterministic reconstruction from the pinned HMT source;
- source-drift and unsafe-path negative checks;
- Java 17 / Forge 47.4.23 compilation;
- compile-checked LWJGL Vulkan backend JAR;
- GLSL to SPIR-V compilation + packaged shader verification;
- packaged Forge 47.4.23 dedicated-server boot;
- dense overlapping vanilla async-mob fixture sized to exercise the GPU candidate-capacity path;
- 10 consecutive verified Vulkan batches;
- live `/async gpu test` with zero false negatives;
- live Vulkan-off vanilla fallback;
- palette mutation + `save-all flush`;
- clean stop and restart of the same world;
- persisted-entity proof;
- another 10 consecutive Vulkan batches and live verifier after restart;
- real Forge client under Xvfb/Mesa loading the saved QA world via Quick Play;
- actual Render-thread evidence + integrated-server sustained Vulkan proof;
- actual Minecraft-window PNG evidence;
- clean client/integrated-server shutdown.

This is a release-grade engineering target, not a claim that asynchronous modification of an arbitrary third-party modpack can be mathematically guaranteed bug-free. The mod keeps synchronous fallbacks and circuit breakers specifically so unknown mod behavior fails safe.
