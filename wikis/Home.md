# Minecraft Vulkan Hybrid

Canonical repository: [Minecraft-Vulkan-Hybrid](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid). Complete migration and development source are in [draft PR #1](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/pull/1), on [migration/projectdump-20261004](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/tree/migration/projectdump-20261004). No main merge or release occurred.

The project retains 1,001 hash-verified accepted source files, 402 relevant commits across 43 filtered branches, original recipes/assets/licenses and provenance. Desktop: `C:\Users\Owner\Desktop\Minecraft Vulkan Hybrid`. Minecraft 1.20.1 / Java 17; accepted source Forge 47.4.23, isolated development Forge 47.4.26 through the logged-in official Modrinth backend.

- [Build and Windows launch](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/docs/BUILD.md)
- [Hari + DimThreads current .11 integration](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/integration/dimensions/README.md)
- [Migration, licenses and preserved history](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/docs/MIGRATION.md)
- [Native grass measurements, raw frames and limits](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/cumulative-foundation-20261005/REPORT.md)
- [Historical Windows matched vanilla results](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/windows-20261004/REPORT.md)
- [Historical rejected full Noxviola comparison](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/noxviola-matched-20261005/REPORT.md)
- [Original project release record](HariMultiThread-Vulkan-Hybrid)

Native grass remains visible with its original private JAR, density/range and visual settings. Six ABBAAB .9/.10 captures, toggling only Hari, measured median **1,866.79 to 2,617.09 FPS (+40.19%)**, 1% low 490.58 to 616.75, p99 1.9118 to 0.7340 ms. Latest .11 additionally invalidates exact frustum/FOV/mode changes; its capability repeats were **2,529.28 / 2,590.76 / 2,502.04 FPS**, with seven bounded reload/animation/trail/FOV/dimension checks passing. Reviewed screenshots show original grass at 70 and 110 degrees. Separate minimal .10 plus recorder-only vanilla scene measured median **4,451.34 FPS**.

These are still-scene CPU RenderTick END intervals at unchanged 1920x1080, RD16/SD12 Fancy settings, not GPU timestamps or presented-frame throughput. Other workloads remained active and contamination is retained. The 350-mod cumulative rebuild continues. Full Noxviola, active Oculus/Iris native interoperability, every grass style and zero hitching remain unfinished. Embeddium and Zink are excluded by user direction. New desktop development artifacts include hashes and no automatic recorder; personal Noxviola and GameSync/AoA/Enderloom remain untouched.

The earlier complete-pack NVIDIA OpenGL candidate remains rejected: median FPS 219.39 to 175.22, despite repeated startup 166.40 to 152.82 seconds. Historical Linux/Mesa or rejected missing-grass captures do not establish current native success. Original accepted 2.4.10 and archives remain unchanged.

Current published source: [16f39e03da1fcfe404b781191fe94d0fd1bc52bd](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/commit/16f39e03da1fcfe404b781191fe94d0fd1bc52bd). Its build/production checks are [passed in CI](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/actions/runs/37284313250); the prior 4049e54 runs passed. All development resource metadata is format 15.

The durable source pointer is prepared in [ProjectDump PR #30](https://github.com/Herbertofury/ProjectDump/pull/30). ProjectDump's hourly wiki mirror overwrites direct edits; that pointer still awaits separately authorized merge. This migration does not change access permissions or publish private mod assets.
