# Actual Noxviola matched native OpenGL results

**Promotion rejected.** Median average FPS: 219.39→175.22 (-20.13%). Median game-to-world startup: 166.40→152.82 seconds (-8.16%). Startup improved during these repeated launches; FPS and frame tails regressed. The full-pack 2,432–2,470 FPS target remains unfulfilled. Live Noxviola was not modified.

| Trial | Average FPS | 1% low FPS | p95 ms | p99 ms | Startup s |
|---|---:|---:|---:|---:|---:|
| 01-baseline | 223.83 | 80.41 | 10.2302 | 11.6543 | 152.72 |
| 02-candidate | 168.91 | 56.73 | 12.9039 | 15.6793 | 152.82 |
| 03-candidate | 208.88 | 72.23 | 10.9150 | 12.6912 | 140.86 |
| 04-baseline | 219.39 | 76.15 | 10.3050 | 11.9764 | 166.40 |
| 05-baseline | 208.16 | 72.59 | 10.6308 | 12.6698 | 168.89 |
| 06-candidate | 175.22 | 62.83 | 12.1155 | 14.1935 | 155.50 |

Three runs per variant in ABBAAB order, October 5 UTC / October 4 local computer time. Minecraft 1.20.1, Forge 47.4.26, Temurin 17.0.20.1, 12 GiB heap, i9-13900K (32 logical processors), 64 GiB RAM, RTX 4090 (24 GiB), NVIDIA driver 610.88. 1920×1080, render distance 12, simulation distance 11, Fast graphics, mipmaps 4, entity range 100%, biome blend 2, all particles, FOV 70, VSync off, unlimited FPS. Shader functionality retained with no shader pack selected. Full visual options and mod hashes are in each inputs.json.

Each trial restores the same checksummed 236-file private creative world, seed 1729, daytime 6000, camera (0.5,110.5,0.5), yaw -68.7007 and pitch 9.999512. Warmup: 60 seconds. Capture: 30 seconds, all Forge RenderTick END wall intervals retained in deterministic fps.json.gz. These are render-loop intervals, not GPU/display presentation timestamps. Average FPS is count divided by summed duration; 1% low uses the mean of the slowest ceil(1% of frames); percentiles use nearest rank.

Baseline: original custom DimThreads v5, Field Guide 1.17.0 and ModernFix 5.27.83. Candidate: integrated Hari/DimThreads 2.4.11-noxviola.2, Field Guide 1.20.4+1.20.1, ModernFix 5.27.85 and Field Guide query addon 1.1.1. Both retain content mods and share duplicate Oculus/Uranus cleanup (custom variants retained), Cataclysm transformer repair, Boilerbox model repair and GeckoLib render-owner texture fix. Exact per-run manifests record all changes. Hari selects OpenGL fallback for the pack's mutually exclusive Embeddium/Oculus/ImmediatelyFast/Indigo renderers. Both variants use native NVIDIA OpenGL. Multiple components changed; this aggregate result does not assign cost to a particular mod.

All six trials saved and exited normally without audited thread-ownership, ticking-world, missing-model or mixin-application failures. The integrated candidate separately passed [seven native smoke checks](../noxviola-development-20261005/STATUS.md), including resource reload, modded animated entity death and Nether round trip. Coverage is bounded; it is not exhaustive gameplay certification.

Per-second Java CPU/RSS and whole-adapter NVIDIA GPU load, clocks, power and memory are retained. GameSync and AoA/Enderloom remained running. Whole-GPU counters include other projects and cannot assign load to Minecraft. First A/B capture median utilization was about 38%/37%; shared workload and run variance limit conclusions. No local build ran during this series. Filesystem/OS caches remained warm; startup values do not describe a cold Windows boot. The earlier 2,400+ vanilla benchmark uses different generated terrain and a different recorder, so it is not a controlled mod-only delta against this full-pack scene.

A separate cumulative mod-addition study starts from the original vanilla scene. It remains incomplete. These results support no claim of 2,400+ full-pack FPS or no hitching.

[Baseline screenshot](04-baseline/scene.png) and [candidate screenshot](06-candidate/scene.png) retain the same terrain, HUD, held guide, textures and camera in the captured scene. Dynamic water/vegetation differences are expected. This static inspection does not certify every mod feature.
