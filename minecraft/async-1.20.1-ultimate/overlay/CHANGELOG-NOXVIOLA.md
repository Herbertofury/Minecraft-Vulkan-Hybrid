# HariMultiThread Ultimate Changelog

## 2.1.0-noxviola.1 — Forge 1.20.1

### Platform

- Rebased release build on `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`.
- Targeted Minecraft 1.20.1, Java 17, and Forge 47.4.23.
- Corrected generated license metadata to GPL-3.0-only to match the upstream repository license.
- Added deterministic source overlay/hardening scripts and source-drift gates.
- Added production Forge Mixin discovery through the final JAR manifest and generated `harimt.refmap.json` so reobfuscated runtime attachment matches development behavior.
- Hardened ForgeGradle/MixinGradle ordering so annotation-processor hard-reference mappings are available to both `reobfJar` and `reobfJarJar`.

### Entity ticking

- Kept HMT v2 work stealing, affinity routing, dynamic workers, per-dimension locks, circuit breaker, telemetry, and CallerRunsPolicy safety.
- Reserved one logical processor by default instead of auto-allocating every available hardware thread.
- Forced Ender Dragon to synchronous ticking, matching later Async correctness work.
- Added clean worker/GPU/deferred-state reset on server shutdown/restart.

### Entity-section / tracking safety

- Hardened `EntitySection` add/remove/isEmpty/untyped/typed reads using short locked snapshots.
- Synchronized tracked-entity update/removal broadcast paths to reduce dead client ghosts.
- Replaced production-fragile decompiled target-member shadows with mapped accessors/invokers where Forge 1.20.1 runtime attachment required it.

### Spawn / chunk ticking

- Removed global `parallelStream()` from asynchronous spawn-state construction.
- Replaced custom concurrent SpawnState collection substitutions with synchronized calls into the original vanilla Forge 1.20.1 methods.
- Changed async `spawnForChunk` and optional random ticks from fire-and-forget tasks into same-tick managed batches with a barrier.
- Final Forge spawn tasks close over the exact `LevelChunk` already supplied by vanilla; no worker-side chunk relookup, `SpawnChunkContext`, or scheduled-chunk shim mixin is packaged.
- Added current-dimension-only chunk-task pumping while waiting on HMT workers for safer DimThread nesting.
- Added both `c2me` and `c2meforge` compatibility detection.

### Save / palette safety

- Added a Forge 1.20.1 `PalettedContainer` read/write lock covering palette mutation, network serialization, save packing, copies, scans, and count/read paths.

### Vulkan broad phase

- Turned the original unused GPU precompute into a real acceleration path for post-barrier living-entity push/crowding queries.
- Made push deferral a whole active-world-batch invariant, covering synchronous fallback entities as well as async workers before stable replay.
- Kept vanilla selectors, cramming, Forge logic, `doPush`, and double-precision AABB checks authoritative.
- Switched GPU inputs to actual runtime entity AABBs with conservative outward float rounding.
- Corrected the shader/pipeline descriptor ABI to nine SSBO bindings.
- Corrected the push-constant ABI to 16 bytes.
- Added mandatory GLSL → SPIR-V compilation in CI and packaged `.spv` verification.
- Removed the invalid raw-GLSL-as-SPIR-V fallback.
- Treats pair-buffer overflow or incomplete GPU results as a full vanilla fallback instead of truncating collision physics.
- Added adaptive candidate-pair capacity with learned high-water reuse; capacity growth is telemetry/optimization rather than a requirement for every live batch.
- Made GPU availability truthful: a Vulkan device alone no longer counts as an operational collision pipeline.
- Added `/async gpu test` as a real live Vulkan-vs-exact-CPU AABB verifier; a single false negative disables GPU acceleration and persists the safe setting.
- Added explicit runtime markers for verified Vulkan dispatch and vanilla fallback.

### Verification

Release workflow gates include:

- Forge 47.4.23 / Java 17 compile and build;
- source drift and unsafe-path negative checks;
- production Mixin manifest/refmap verification in the reobfuscated JAR;
- valid SPIR-V generation and JAR packaging;
- release identity/license checks;
- packaged Forge dedicated-server startup;
- dense overlapping-mob Vulkan broad-phase proof;
- sustained verified Vulkan batches plus live exact GPU-vs-CPU verification;
- live Vulkan disable → vanilla fallback proof;
- palette mutation and forced world save;
- clean stop/restart of the same world;
- persisted-entity proof and fresh post-restart Vulkan verification;
- real Forge client + integrated-server launch under Xvfb/Mesa;
- render-thread evidence, actual Minecraft-window screenshot, and clean client shutdown.
