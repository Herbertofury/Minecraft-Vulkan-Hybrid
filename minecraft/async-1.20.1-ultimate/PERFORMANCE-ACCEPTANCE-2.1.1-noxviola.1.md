# HariMultiThread Ultimate 2.1.1-noxviola.1 Performance Acceptance

## Accepted optimization

The deferred-push Vulkan broad phase now queries only live entities intersecting the conservative union of the deferred source AABBs instead of uploading every live entity in the dimension. Vanilla spatial indexing removes impossible distant candidates; Hari's exact replay predicate, double-precision AABB intersection, Forge hooks, cramming rules, and `doPush` remain authoritative.

## Real Forge A/B acceptance

- Repository: `Herbertofury/ProjectDump`
- Performance branch: `async-1.20.1-ultimate-perf-lab-20260912`
- Acceptance commit: `a5733940777a8397bb0441ac7869b7707092d258`
- GitHub Actions run: `34729147864`
- Evidence artifact: `10308663653`
- Artifact digest: `sha256:dca356b50f3d21903a265819ae65237f562396c6d21ba710a5cdbf16f801af0b`
- Minecraft: 1.20.1
- Forge: 47.4.23
- Java: 17
- Pinned upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`

### Noisy workload result

Workload: 256 overlapping no-AI/no-gravity cows plus 512 distant loaded marker entities.

- Median MSPT: `15.637284279 -> 13.732396126` = **12.18% faster**
- Mean MSPT: `16.024341447 -> 13.616457803` = **15.03% faster**
- Last Vulkan dispatch: `3.158748 ms -> 0.963303 ms` = **69.50% faster**

Acceptance thresholds were >=10% median MSPT gain, >=10% mean MSPT gain, and >=35% Vulkan dispatch gain.

### Normal-workload regression gates

- Spread 256: median MSPT improved 13.57%; mean improved 0.07%; Vulkan dispatch improved 3.05%.
- Dense 256: median MSPT regressed 1.64%; mean regressed 0.19%; Vulkan dispatch regressed 3.53%.
- Allowed bands: <=5% MSPT regression and <=8% Vulkan-dispatch regression.

### Exact population parity

All 42 timed population observations matched the benchmark contract:

- spread baseline/candidate: exactly 256 `harimt_perf`, 0 `harimt_noise` on all seven samples;
- dense baseline/candidate: exactly 256 `harimt_perf`, 0 `harimt_noise` on all seven samples;
- noisy baseline/candidate: exactly 256 `harimt_perf`, 512 `harimt_noise` on all seven samples.

### Packaged runtime correctness

The candidate also passed the packaged Forge 47.4.23 correctness gate, including sustained Vulkan broad phase, live exact GPU-vs-CPU verification, Vulkan-off fallback, save/flush, clean stop, same-world restart, and post-restart Vulkan verification. No task-related fatal marker was observed.

## Benchmark artifact hashes

These hashes identify the benchmark-instrumented A/B JARs, not the final 2.1.1 release JAR:

- Baseline JAR: `32fcadb8d6268d6c2ed66f1c69d31fff561539af56b9d97da8473c6797bf236a`
- Spatial candidate JAR: `d06a556bb2c7b6b14c3c8f9110a4e71a1f1192370348835d0a03b930d4e4961a`

## Canonical-grade promotion QA

- Promotion branch commit: `2482729f8d8848ad9019a538d68972fafc27b152`
- Promotion workflow run: `34729733922`
- Promotion evidence artifact: `10308689296`
- Promotion artifact digest: `sha256:a5cba53d3cb57a8ccd3310c4424feefa7cf5684604a910886612762dba5ac605`
- Promotion candidate JAR SHA256: `f49bf72d1ebf06879b03facf938f5e6850d19e04fbc5e98f68425f232459ff70`

That production-source candidate passed deterministic reconstruction, final compatibility audit, Forge packaging invariants, packaged dedicated-server Vulkan/fallback/save/restart QA, and the real Forge client + integrated-server rendered-world gate. The promotion artifact intentionally still carried the old 2.1.0 version string; the final release changes that metadata to 2.1.1 and reruns the full canonical workflow before publication.

## Hardware caveat

The GitHub-hosted benchmark runner exposed 4 vCPUs of an AMD EPYC 7763 and Mesa llvmpipe CPU Vulkan (`PHYSICAL_DEVICE_TYPE_CPU`). These are genuine Forge/runtime A/B gains for the tested workload and establish that dimension-wide unrelated entities were unnecessary broad-phase input. They are **not** a claim that Vulkan dispatch will improve by the same percentage on a specific discrete GPU such as an RTX 4090.
