# HariMultiThread Ultimate 2.2.0-noxviola.1

Target: **Minecraft 1.20.1 / Forge 47.4.23 / Java 17**

This release combines the accepted HariMultiThread Ultimate 2.1.1 async/Vulkan performance stack with result-preserving performance behavior backported from Minecraft Java Edition 26.3. It is designed to cooperate with the rest of the performance stack instead of replacing every subsystem itself.

## Minecraft 26.3 backports

### Persistent-mob idle behavior

Minecraft 26.3 no longer keeps persistent mobs' idle timer pinned to zero merely because they are persistent. Hari backports that behavior to 1.20.1 with a minimal `Mob.checkDespawn` correction:

- persistent/custom-persistent mobs remain exempt from despawning exactly as before;
- when no player is inside the mob category's vanilla no-despawn radius, `noActionTime` is allowed to accumulate;
- existing vanilla random-stroll/random-swim goals naturally become idle when their normal thresholds are reached;
- targeting, navigation, goals, persistence and despawn eligibility are not replaced.

Real Forge QA verifies the behavior with a live tagged persistent mob and reads its actual `noActionTime` counter through `/async mc263 test`.

### Faster structure locating — including no empty region-file litter

Hari backports the safe 26.3 structure-locate improvements in two cooperating layers.

First, it adds a bounded, thread-safe cache for chunks whose structure metadata lookup already found no stored structure data. That avoids redundant region/NBT miss reads on later checks while leaving vanilla's `canCreateStructure` decision authoritative. Cache entries are invalidated when structure data or references are recorded.

Second, it backports the non-creating missing-region read path. Minecraft 1.20.1 can construct/open a `RegionFile` while merely probing an ungenerated candidate chunk, which can leave empty `r.<x>.<z>.mca` files behind after `/locate`. Hari checks whether the `.mca` region actually exists before read/scan, remembers definite missing regions in a bounded cache, and returns the normal empty read/scan result without creating a file. The missing-region cache is invalidated before any write, so successful region reads, chunk generation, saves and the Anvil region-file format remain vanilla.

The final real Forge release gate snapshots `world/region`, runs `/locate structure minecraft:pillager_outpost`, and requires **zero new `.mca` files** from the locate itself. Repeated stronghold locating must also return the same coordinates.

### Experimental chunk-generation density-cache fast path

Hari contains a narrow 1.20.1 backport experiment based on the cache-aware part of 26.3's density-function/cache redesign. It keeps 1.20.1 **double-precision** density evaluation and can route `NoiseChunk.Cache2D` bulk fills through the existing X/Z cache rather than bypassing it.

It is **OFF by default in 2.2.0**. In the real Forge ABBA lab, the candidate median was about **0.42% slower** while the mean was about **0.86% faster**, which did not meet the >=1% median-gain promotion rule. The initial cross-JVM numeric block-state hash was also not accepted as terrain-parity proof because baseline A1 and A2 themselves produced different numeric-ID hashes. Keeping this lane disabled preserves the accepted 1.20.1 world-generation path by default while leaving the experiment available for controlled testing. See `PERFORMANCE-ACCEPTANCE-2.2.0-noxviola.1.md` in the release.

## 26.3 technology provider precedence

Hari deliberately does not create a second invasive owner when another established mod already owns the subsystem:

- **Embeddium / Rubidium / Sodium:** terrain rendering remains theirs; 26.3 MultiDrawIndirect terrain ownership is delegated.
- **Oculus / Iris:** shader/transparency ownership remains theirs; 26.3 OIT/ShaderC client lanes are delegated.
- **C2ME / HariChunk:** chunk-generation scheduler ownership remains theirs; Hari disables its Cache2D mixin when that provider is present.
- **Noisium:** remains additive; its generator/section fast paths coexist with Hari's narrow result-preserving cache lane.
- **Entity Culling / ImmediatelyFast / GPUTape / BadOptimizations:** Hari continues to avoid Minecraft renderer/OpenGL ownership entirely.
- **Region storage:** Hari only short-circuits definite missing-file read/scan requests; successful reads and all writes stay on the vanilla storage path.

These rules are enforced by the release compatibility audit, not merely documented.

## What is intentionally not forced into 1.20.1 core

Minecraft 26.3 also changes the density representation/interpolation model, uses order-independent transparency, migrates window/input handling to SDL3, compiles OpenGL shaders through ShaderC, and ships with a newer Java runtime configuration. Those changes are not blindly transplanted into Forge 1.20.1 core:

- 26.3 single-precision/interpolation rewrites can change terrain and are excluded from the forever-world/default profile without parity proof;
- OIT is a visual/rendering architecture change and is not treated as a universal performance optimization;
- SDL3 and ShaderC replace bootstrap/renderer contracts and are left to compatible client providers rather than injected beside Forge/Embeddium/Oculus;
- the certified build remains Java 17, matching Forge 1.20.1's validated runtime instead of forcing a newer JVM from inside the mod.

## Operator diagnostics

`/async mc263` reports the live state of persistent-mob idling, structure caching, non-creating region reads, the experimental Cache2D lane, and the detected subsystem owners.

`/async mc263 test` performs a live persistent-mob idle verification when the QA fixture is present.

## Preserved 2.1.1 performance work

All accepted 2.1.1 hardening remains: affinity/work stealing, circuit breaker behavior, conservative third-party entity synchronization, C2ME/DimThread interop, lifecycle cleanup, same-tick spawn/random-tick barriers, packaged refmap/mixin hardening, and the optional isolated Vulkan collision broad phase with exact vanilla fallback.

The previous accepted noisy-workload A/B result remains part of the inherited baseline; 2.2.0 does not remove content, lower entity caps, reduce render distance, or require Vulkan.

## Upgrade

Replace the previous HariMultiThread Ultimate 1.20.1 JAR with the verified 2.2.0 JAR. Keep only one HariMultiThread/Hari Async JAR in the `mods` folder. Existing worlds require no conversion from Hari itself.

The canonical release bundle includes the JAR, reconstructed source, runtime/performance evidence, SHA-256 checksums, the 26.3 backport matrix, compatibility audit, and the full performance acceptance record.
