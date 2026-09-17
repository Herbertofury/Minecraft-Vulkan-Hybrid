# HariMultiThread Ultimate 2.2.0-noxviola.1 Performance Acceptance

Target: **Minecraft 1.20.1 / Forge 47.4.23 / Java 17**

Pinned upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`

## Acceptance decision

The 2.2.0 release keeps the already-accepted HariMultiThread Ultimate 2.1.1 async/Vulkan performance work and adds two default-on Minecraft 26.3-derived optimizations that passed real Forge behavioral QA:

1. persistent-mob idle/noActionTime behavior;
2. bounded repeated structure-metadata storage-miss caching.

The 26.3-inspired `NoiseChunk.Cache2D` bulk-fill path is included as an **experimental option but defaults OFF**. Its real Forge ABBA benchmark did not meet the promotion threshold, and the initial cross-JVM numeric block-state hash was not a valid terrain-parity proof because baseline A1 and baseline A2 also produced different numeric-ID hashes. The release therefore does not alter the normal world-generation path by default.

## Minecraft 26.3 runtime acceptance

Repository: `Herbertofury/ProjectDump`

Working branch: `async-1.20.1-ultimate-mc263-20260917`

Accepted regression head: `6c485f24ec60cd022177baf3b7f65bec688b0b4b`

GitHub Actions run: `35273539972`

Evidence artifact: `10519689717`

Artifact digest: `sha256:68cebb199bd655ece9665c0ee019b0cd174c959e12c8a47a7a74d13dc49332d5`

The run passed deterministic source transformation/invariants, Forge compilation, packaged Mixin/refmap verification, a real Forge 47.4.23 dedicated-server install, live Minecraft 26.3 behavior QA, fresh distant chunk generation, save/flush and clean shutdown.

### Persistent-mob idle proof

A real Forge server spawned a tagged persistent mob and `/async mc263 test` inspected its live `noActionTime`. The observed maximum was:

- `noActionTime = 102`

Vanilla 1.20.1's persistent-mob path would keep that value reset to zero. The backport therefore demonstrated the intended 26.3 idle-state behavior without replacing goals or changing despawn eligibility.

### Structure-locate semantic proof

Two consecutive real-server commands:

- `/locate structure minecraft:stronghold`
- `/locate structure minecraft:stronghold`

returned the same X/Z result:

- `[1376, 848]`

Observed command timings were approximately `0.050241 s` and `0.051312 s`. These noisy command-level timings are **not** claimed as a measured speedup. The accepted benefit is the implementation-level elimination of repeated storage/NBT miss reads while leaving vanilla `canCreateStructure` authoritative.

### Fresh chunk-generation execution proof

The runtime gate forced genuinely fresh distant chunks through the transformed build and then completed a save/flush and clean stop with no Mixin/runtime fatal marker. This is an execution-safety gate for the 26.3 worldgen layer, not a performance claim for the experimental Cache2D option.

## Cache2D real Forge ABBA result

Benchmark run: `35273540019`

Evidence artifact: `10519780970`

Artifact digest: `sha256:de0bf85445d32770b0cbed3759ce0ce1e2c052181b424047b907e9c66a2f032a`

Head SHA: `6c485f24ec60cd022177baf3b7f65bec688b0b4b`

Design: one identical built JAR, Forge 47.4.23, Java 17, seed `8675309`, ABBA order, identical fresh chunk coordinates, 7 samples per run, 64 chunks per sample. Combined sample counts were 14 baseline and 14 candidate samples.

### Timing result

- Baseline median: `3679.635061 ms`
- Candidate median: `3694.9099395 ms`
- Median change: **0.4151% slower**
- Baseline mean: `3732.045654857143 ms`
- Candidate mean: `3700.0724582142857 ms`
- Mean change: **0.8567% faster**

Promotion rule required at least **1.0% median gain** and **no mean regression**. The candidate failed the median-gain threshold and is therefore **DEFAULT OFF**.

### Terrain-hash caveat and safety action

The first benchmark harness used `Block.getId(BlockState)` to construct a post-timing terrain hash. Those numeric hashes differed even between baseline A1 and baseline A2 at the same seed and benchmark coordinates, so they are not accepted as a cross-JVM semantic-parity proof. The comparison gate correctly refused promotion rather than treating the hash as authoritative.

Safety action: the Cache2D implementation remains available for controlled experimentation, automatically yields to HariChunk/C2ME, but is disabled in the 2.2.0 default configuration. The normal release profile therefore retains the previously validated Minecraft 1.20.1 density/world-generation path.

## Provider-precedence acceptance

The release compatibility audit enforces one invasive owner per subsystem:

- Embeddium/Rubidium/Sodium own terrain submission; Hari does not install a competing MultiDrawIndirect renderer.
- Oculus/Iris own shader/transparency pipelines; Hari does not replace OIT/ShaderC paths beside them.
- C2ME/HariChunk owns the chunk scheduler; Hari's experimental Cache2D mixin is disabled when that provider is present.
- Noisium remains additive; Hari does not duplicate its generator/section fast paths.
- Hari remains free of Minecraft client renderer/Blaze3D/OpenGL ownership for Entity Culling, ImmediatelyFast, GPUTape and BadOptimizations compatibility.

## Inherited 2.1.1 performance acceptance

The 2.1.1 deferred-push spatial/Vulkan optimization remains unchanged and accepted from its real Forge A/B run `34729147864`, artifact `10308663653`, digest `sha256:dca356b50f3d21903a265819ae65237f562396c6d21ba710a5cdbf16f801af0b`.

Noisy workload (256 overlapping no-AI/no-gravity cows plus 512 distant loaded marker entities):

- Median MSPT: `15.637284279 -> 13.732396126` = **12.18% faster**
- Mean MSPT: `16.024341447 -> 13.616457803` = **15.03% faster**
- Last Vulkan dispatch: `3.158748 ms -> 0.963303 ms` = **69.50% faster**

The candidate also passed its packaged Forge correctness, save/restart, Vulkan verification/fallback, and real client/integrated-server gates. 2.2.0 preserves those accepted paths and adds the 26.3 behavior/compatibility layers above.

## Release requirement

2.2.0 is publishable only after the canonical release workflow passes the full production reconstruction, compatibility audit, shader/SPIR-V packaging, real packaged Forge server Vulkan/fallback/save/restart gate, the new MC26.3 runtime gate, and the real Forge client/integrated-server rendered-world gate.
