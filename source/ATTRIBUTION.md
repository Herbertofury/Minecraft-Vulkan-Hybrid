# Attribution and Source Lineage

HariMultiThread Ultimate is a continuation/fork build. It does not claim the original work as new authorship.

## Canonical source base

- Project: **HariMultiThread**
- Repository: `JustHari01/HariMultiThread`
- Pinned commit: `f381611c2d71a85192e2028f9e30c03823a6482b`
- Base work includes the HariMultiThread v2 worker architecture, affinity/work stealing, circuit breaker, per-dimension locking, telemetry, and initial Vulkan collision infrastructure.

## Async lineage

HariMultiThread itself derives from the Async entity multithreading project. This release also reviewed later Async work for correctness fixes and adapted only changes applicable to Forge 1.20.1.

- Project: **Async**
- Repository: `AxalotLDev/Async`
- Reviewed correctness commit: `a8fdb87b78064b5fdca40a7878eb177cbdcd130f`
- Relevant upstream findings included Ender Dragon synchronization, ghost-entity removal, async-spawn correctness, chunk-palette save synchronization, and worker/barrier improvements.

## Forge 1.20.1 Async fork

- Project: **Async-1.20.1**
- Repository: `freanz3000/Async-1.20.1`
- Relevant thread-safety commit: `9c8d2a8f64305e9a5c0a55bc1831ccd84727444b`
- Its `EntitySection` deadlock/concurrent-read hardening informed the 1.20.1 implementation here. HariMultiThread Ultimate extends that idea by snapshotting all relevant EntitySection read paths while the same short storage lock is held, then iterating outside the lock.

## Noxviola-specific release hardening

The `2.1.0-noxviola.1` release line adds/changes, among other things:

- real use of Vulkan broad-phase candidates in deferred vanilla living-entity push processing;
- corrected Vulkan descriptor/push-constant/shader ABI;
- build-time GLSL → SPIR-V packaging;
- complete fallback on GPU failure/output overflow;
- runtime-AABB conservative GPU inputs and vanilla double-precision narrow phase;
- synchronized vanilla SpawnState access;
- Forge 1.20.1 PalettedContainer save/read protection;
- same-tick barriers for parallel spawn/random-tick work;
- Ender Dragon synchronous safety;
- current-dimension-only chunk-queue pumping for DimThread interop;
- `c2me` + `c2meforge` detection;
- clean server restart lifecycle;
- Forge 47.4.23 target and reproducible QA workflow.

## License

The canonical HariMultiThread repository includes a GNU GPL v3 license. HariMultiThread Ultimate therefore publishes its derivative source under **GPL-3.0-only** and keeps the upstream `LICENSE` in the merged source package.

The original HMT `gradle.properties` advertised `CC0-1.0`; this release intentionally corrects generated mod metadata to match the repository's GPL-3.0 license rather than propagating conflicting metadata.
