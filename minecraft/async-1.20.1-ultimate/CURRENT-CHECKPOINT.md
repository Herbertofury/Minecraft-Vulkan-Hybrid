# HariMultiThread Vulkan Hybrid — current work checkpoint

Updated: 2026-10-01. Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Canonical branch: `async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926`.

The 2.4.1 sensor-repaired performance candidate is implemented at product `170784207b0c9093c850c629c19f0bcfff54bef7`. JAR: 30,708,500 bytes; SHA-256 `13710068df209f3216c11af5c4931c944072cf874ffabd97a3176a703b1de5cc`. Compile/repro run 36823011441 passed every focused regression, stable sensor-order negative controls, packaging/source decompiler-stub guards and byte-identical clean rebuild. Complete merged source has 985 files and is byte-identical to a fresh 23-step immutable-source reconstruction; all 44 omitted builder files are restored.

Native server/C2ME run 36823011335, seven packaged client lanes run 36823011386 and strict Khronos/C2ME chunk travel at QA commit `3afa86397713cdf6439ee4ac39ed76b01464110c` are pending. The latter also exercises nectar bee, villagers, Piglin/item sensing and two native entity workers. Disposable C2ME configs use version 3 and strict world-RNG enforcement; logs must prove enforcement is active. The candidate is not yet promoted.

Earlier performance binaries are superseded: 418ed90 had the passenger-spawn dimension-lock cycle, f98d69e had a C2ME chunk-future owner-queue dependency, and 22f4bda passed main client/server gates but broader strict chunk travel exposed three inherited executable CFR sensor throws. Live evidence is preserved. The final source hands off before locks, uses Minecraft managed blocking with a full failure barrier, and restores all three comparator snapshots while retaining vanilla disabled behavior. Never swallow ticks, weaken C2ME checks or ship a superseded binary.

See PERFORMANCE-RELEASE-CHECKPOINT.json for exact next actions and VULKAN-PERFORMANCE-2.4.1.md for implementation and source research. Older integrity evidence is historical; the new sensor repair is required. Native proof is Linux Mesa software, not a Windows/RTX 4090 FPS benchmark.
