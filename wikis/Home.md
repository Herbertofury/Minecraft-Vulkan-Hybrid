# Minecraft Vulkan Hybrid

Canonical repository: [Minecraft-Vulkan-Hybrid](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid). Complete migration: [migration/projectdump-20261004](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/tree/migration/projectdump-20261004) in [draft PR #1](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/pull/1). Main remains unchanged pending review.

- [Project and historical release evidence](HariMultiThread-Vulkan-Hybrid)
- [Build and Windows launch instructions](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/docs/BUILD.md)
- [Migration scope, licenses and history](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/docs/MIGRATION.md)
- [Completed actual Windows/RTX 4090 benchmark](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/windows-20261004/REPORT.md)

The migrated source includes 1,001 verified accepted files, reconstruction recipes, tests, shaders/assets and attribution. 43 filtered project branches retain 402 relevant commits. Windows Java 17 build and focused regressions passed, and destination CI builds the complete source.

Actual Minecraft 1.20.1 tests used Java 17, Modrinth 0.21.6 and latest official Forge 47.4.26 in isolated instances on i9-13900K / RTX 4090 (driver 610.88). Six matched runs produced median FPS 2,432.29 → 2,470.06 (+1.55%), 1% low 440.41 → 503.56 (+14.34%), but p99 0.8864 → 1.0483 ms. Overlapping ranges, mixed tails and concurrent GPU work prevent a reliable new FPS improvement claim; a performance promotion is rejected. No lower visual settings or workload termination was used. A separate original packaged Minecraft JVM passed exact RGBA/R8 GPU upload/readback across three mip levels.

Historical Linux software-driver acceptance is preserved separately below the project page and must not be presented as this PC's performance. The source acceptance build stays pinned to Forge 47.4.23; the requested latest loader has a separately verified installed-runtime route in Modrinth because its upstream catalog is stale at 47.4.20.
