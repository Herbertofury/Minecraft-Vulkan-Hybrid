# HariMultiThread Ultimate — Forge 1.20.1

A release-hardened Forge 1.20.1 continuation of **HariMultiThread / Async** focused on aggressive server-side entity parallelism with conservative correctness fallbacks.

## Target

- Minecraft: **1.20.1**
- Forge: **47.4.23**
- Java: **17**
- Mod id: `harimt`
- Release line: `2.1.0-noxviola.1`
- License: **GPL-3.0-only** (see `LICENSE`)
- Canonical source base: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`

Do not install another copy of HariMultiThread/Async with this JAR. This is a continuation/fork build, not an addon.

## What it does

HariMultiThread moves eligible server-side entity ticking onto a managed worker pool. The release keeps HMT's work stealing, chunk-affinity routing, dynamic workers, per-entity-type circuit breaker, per-dimension locks, telemetry, asynchronous spawn support, and optional Vulkan compute path.

This release additionally hardens the parts that were unsafe or incomplete in the source base:

- reserves one logical CPU by default instead of occupying every hardware thread;
- keeps Ender Dragon ticking synchronous after upstream Async found landing/fireball regressions;
- snapshots `EntitySection` reads under a short lock to prevent collision-query CMEs/deadlocks;
- serializes vanilla `NaturalSpawner.SpawnState` mutation rather than replacing vanilla collection identities;
- keeps parallel spawn/random-tick work inside the same server tick instead of fire-and-forget jobs leaking into later ticks;
- protects `PalettedContainer` mutation/serialization with a read/write lock to avoid save-time palette races;
- synchronizes tracked-entity removal broadcasts to avoid persistent client ghost entities;
- cleans worker/GPU/deferred state on server shutdown so integrated-server restarts start cleanly;
- recognizes both `c2me` and `c2meforge` for C2ME interop;
- pumps chunk tasks only for the current dimension while waiting on HMT workers, avoiding cross-dimension queue execution under DimThread.

## Vulkan collision acceleration

The original HMT Vulkan code computed broad-phase pairs but did not actually consume them in Minecraft collision work. Its shader/pipeline ABI also did not agree, source builds did not compile the required SPIR-V, and overflowing the fixed pair buffer could silently truncate results.

HariMultiThread Ultimate turns this into a real, fail-safe acceleration path:

1. `LivingEntity.pushEntities()` invoked by HMT entity workers is deferred until the HMT worker barrier.
2. Entity positions are stable at that point.
3. Vulkan receives the entities' **actual runtime AABBs**, outward-rounded when converted to float so the GPU broad phase can produce false positives but not precision-induced false negatives.
4. Vulkan produces candidate overlapping pairs.
5. The original vanilla `LivingEntity.pushEntities()` method is replayed on the server thread.
6. Only its exact bounding-box entity lookup may consume the GPU candidate map.
7. Vanilla predicate checks, double-precision AABB intersection, cramming rules, Forge behavior, and `doPush` remain authoritative.

If Vulkan is disabled, unavailable, circuit-broken, overflows its output buffer, or otherwise cannot return a complete result, the same deferred vanilla push method runs with the normal vanilla entity query. **GPU failure never becomes missing collision physics.**

The JAR contains a build-compiled `collision_broadphase.comp.spv`; CI fails if it is absent or invalid.

### Runtime messages

A verified live GPU batch logs:

```text
Vulkan push broad-phase is active: first verified batch produced ... candidate pairs
```

A live fallback logs why vanilla lookup is being used:

```text
Vanilla push replay fallback is active: ...
```

## Commands

HMT's existing `/async` command tree remains available. Useful commands include:

- `/async stats` — asynchronous entity tick statistics;
- `/async gpu` — Vulkan/device/collision status;
- `/async gpu toggle` — live enable/disable of GPU collision acceleration;
- `/async config` — configuration controls exposed by HMT.

## Compatibility philosophy

The default is aggressive parallelism with escape hatches, not blind "async everything":

- entities that HMT already marks unsafe remain synchronous;
- Ender Dragon is explicitly synchronous;
- third-party entities are synchronous unless they opt into HMT's async API;
- per-entity-type circuit breaker can force a failing type back to synchronous ticking;
- chunk waits are mediated through the current dimension's chunk queue;
- C2ME/HariChunk lighting synchronization ownership is respected;
- Vulkan is optional and has a complete vanilla fallback.

The Noxviola target stack uses stock `c2meforge-0.2.0-forge.9-all.jar` plus the `DimThreads 1.2.1 Noxviola SAFE-INTEROP` build. The SAFE-INTEROP build temporarily swaps each active dimension's `ServerLevel` and `ServerChunkCache` main-thread pointer to that dimension worker under a synchronized/finally restoration boundary; HMT Ultimate's interop path is written around that behavior.

## Performance scope

This mod primarily targets **server/integrated-server MSPT/TPS/entity CPU work**, not the client render thread. It can improve client smoothness when the integrated server was causing frame pacing stalls, but it is not a replacement for rendering optimizers such as Embeddium/entity culling/renderer-specific mods.

## Verification

The release workflow is designed to require more than compilation:

- deterministic reconstruction from the pinned HMT source commit;
- source-drift checks before patch application;
- Forge 47.4.23 / Java 17 build;
- GLSL → SPIR-V compilation and packaged-shader verification;
- JAR-content and license/identity gates;
- packaged Forge dedicated-server launch;
- dense overlapping-mob Vulkan dispatch proof;
- live GPU-off vanilla fallback proof;
- palette mutation + `save-all flush`;
- clean stop and restart of the same world;
- persisted entity proof and fresh post-restart Vulkan dispatch.

A release is not described as verified until those applicable runtime gates pass.
