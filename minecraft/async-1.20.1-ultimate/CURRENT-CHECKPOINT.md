# HariMultiThread Ultimate 1.20.1 — Current Checkpoint

Updated: 2026-09-06
Branch: `async-1.20.1-ultimate`
Pinned upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17

## Objective

Ship the strongest practical Forge 1.20.1 async/entity performance fork without content loss, arbitrary caps, silent collision loss, unsafe fire-and-forget work, or a mandatory Vulkan dependency. Vulkan broad phase is an optional accelerator with complete vanilla/CPU fallback and circuit-breaker isolation.

## Verified lineage already preserved

- Canonical source base is HariMultiThread commit `f381611c2d71a85192e2028f9e30c03823a6482b`.
- Earlier hardened Forge build passed Java 17 compile, SPIR-V compilation, JAR-content gates, and Forge 47.4.23 static packaging.
- Noxviola stable C2ME + SAFE-INTEROP DimThread pairing was inspected from the exact Drive bundle; HMT barrier pumping was narrowed to the current `ServerLevel` to avoid cross-dimension queue execution.
- Ender Dragon forced synchronous; default worker count reserves one logical CPU; spawn/random-tick fire-and-forget work removed; same-tick barriers used; SpawnState serialized without replacing vanilla collection identities; PalettedContainer save/read locking added; tracked-entity removal hardened.
- Vulkan descriptor/shader ABI repaired, real SPIR-V build gate added, runtime AABB bounds used, pair overflow fails to full fallback instead of truncating, truthful GPU health/telemetry added, deferred vanilla push replay preserves vanilla narrow-phase semantics.
- Vulkan implementation moved behind an isolated compile-checked backend/runtime architecture to avoid exposing conflicting LWJGL-Vulkan modules through Forge JarJar.

## Latest causal failure and repair

Previous authoritative build head `d22d7ff53312c026fba53ede8c2401899076cfe1` failed at `:common:compileJava` because `CrashGuard.execute(...)` was ambiguous between its `Callable<T>` and `Runnable` overloads in `GpuCollisionDispatcher`.

Repair committed in `407c5b1a25a52e947b31eedef6a03ba585d89496`: the Vulkan compute lambda is explicitly typed as `Callable<VulkanCollisionBackend.Result>` before calling CrashGuard. CrashGuard behavior itself was not weakened or changed.

## Anti-stall CI change

Commit `c99db965fd7d4e3d94918c397924098a3a7fc9bf` removed the duplicate FINAL and RELEASE workflows from the active development branch so a normal branch push launches one authoritative development workflow instead of three heavyweight copies. Those workflows remain recoverable from Git history (`d22d7ff53312c026fba53ede8c2401899076cfe1`) and the full release/native-client workflow will be restored only at convergence.

## Acceptance ledger

- [x] Preserve upstream source lineage and GPL attribution.
- [x] Forge 1.20.1 / Java 17 target.
- [x] Current Forge target 47.4.23.
- [x] Real Vulkan shader compilation and packaged SPIR-V gate.
- [x] No silent GPU collision-pair truncation.
- [x] Vulkan unavailable/failure => complete vanilla/CPU fallback.
- [x] Deferred crowd push retains vanilla narrow-phase/push logic.
- [x] C2ME/DimThread dimension-local barrier interop hardening.
- [x] Spawn/random-tick same-tick barriers; no fire-and-forget bleed.
- [x] Dragon synchronous and one logical CPU reserved by default.
- [x] Lifecycle cleanup for integrated-server restart.
- [ ] Fresh compile of the isolated Vulkan backend after Callable repair.
- [ ] Packaged Forge 47.4.23 dedicated-server runtime proof.
- [ ] Sustained Vulkan dispatch + live fallback + save/restart proof.
- [ ] Real Forge client/integrated-server proof.
- [ ] Final JAR/source/evidence hashes and publication to GitHub release + Google Drive.

## Exact next action

Use the single development workflow triggered by this checkpoint commit. If it fails, fetch the first failing job once, patch the earliest causal owner, and checkpoint. If it passes the packaged Forge server/runtime gates, restore the one-time native client/release workflow from Git history and run it once for final release proof and publication.
