# Minecraft Vulkan Hybrid

Canonical repository for **Minecraft Vulkan Hybrid / HariMultiThread**, Minecraft **1.20.1** and **Java 17**. The immutable accepted 2.4.10 source targets Forge **47.4.23**; the rebuildable 2.4.11 dimension integration targets latest Forge **47.4.26**. Migration is prepared on `migration/projectdump-20261004`; `main` remains unchanged until review.

The complete merged Java/Gradle project, shaders, assets and upstream licenses are in [`source/`](source/). The accepted production JAR SHA256 is `8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f`. The renderer is included; do not add a separate VulkanMod JAR to this instance.

| Directory | Contents |
| --- | --- |
| `integration/dimensions/` | Rebuildable Hari + DimThreads artifact, pinned upstream source, ownership fixes and shared simulation budget |
| `compat/` | Narrow GeckoLib/Field Guide repairs and the optional development OpenGL-over-Vulkan bridge |
| `source/` | 1,001 hash-verified merged source files; build this project directly |
| `minecraft/async-1.20.1-ultimate/` | Original pinned reconstruction recipe, overlays, regression tests and historical project documents; original paths remain executable |
| `.github/qa/` | Project-specific original native QA actors and checkpoints |
| `.github/workflows/` | Current standalone build/check workflow |
| `legacy-workflows/` | Original ProjectDump workflow definitions and immutable historical run references |
| `docs/` | Build, installation, migration and benchmark instructions |
| `provenance/` | Original manifests, original wiki bytes, file checksums and old/new Git object mapping |
| `benchmarks/` | Protocol and actual local results as they become available |
| `artifacts/` | Local binaries; original full archives remain on the desktop and in their existing Drive folder |

## Build

```powershell
python tools/verify_source.py
cd source
.\gradlew.bat --no-daemon --max-workers=4 :forge:build --stacktrace
```

Use Java 17. On Linux use `./gradlew`. The distributable is `source/forge/build/libs/harimt-forge-1.20.1-2.4.10-noxviola.1-vulkan-hybrid-all.jar`. [Build details](docs/BUILD.md) explain Windows packaging differences. [Installation](docs/INSTALL-2.4.10.txt), [migration inventory](provenance/migration-inventory.json), and [attribution](ATTRIBUTION.md) retain the original project's evidence.

## Actual hardware benchmark

Use the isolated Modrinth benchmark instances and [matched protocol](benchmarks/PROTOCOL.md). The existing Linux Mesa software-driver results are historical evidence. [Completed Windows/RTX 4090 evidence](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/windows-20261004/REPORT.md) includes six matched real Minecraft runs and a separate GPU readback/profile. Median FPS was 2,432.29 → 2,470.06 (+1.55%), 1% low 440.41 → 503.56 (+14.34%), while median p99 worsened 0.8864 → 1.0483 ms. Run ranges overlap and other GPU workloads continued, so no reliable new FPS gain or performance promotion is claimed. Both isolated Modrinth instances use latest official Forge 47.4.26; source acceptance/build remains pinned to 47.4.23. [Windows launch requirements](docs/BUILD.md) include the required pre-launch LWJGL stack setting.

Ctrl+F9 records 30 seconds of real frame intervals after warmup. Preserve each `harimt-fps-last.json`. Compare average FPS, 1% low, p95/p99 frametimes, actual rendering, and clean save/restart, with identical world copies/settings/mods and balanced alternating runs. Never stop GameSync, AoA, Enderloom or other workloads for a cleaner number; record overlap.

[Canonical wiki](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/wiki) · [Original artifact folder, unchanged access](https://drive.google.com/drive/folders/16zuPwFiiKZ5ewPIE11keigFUaJvsd717) · [Full provenance and scope](docs/MIGRATION.md)

## Integrated Noxviola development

[Hari + DimThreads source and build instructions](integration/dimensions/README.md) describe the single artifact that replaces both standalone engines while preserving the `dimthread` compatibility identity. [Development evidence](benchmarks/results/noxviola-development-20261005/STATUS.md) separates native playability checks from matched performance measurements. The live personal Noxviola profile remains untouched while these gates run. This full pack has not demonstrated 2400 FPS or zero hitching.

The latest full-pack [matched Noxviola measurements](benchmarks/results/noxviola-matched-20261005/REPORT.md) reject promotion: median FPS 219.39→175.22 despite repeated-launch startup 166.40→152.82 seconds. Native feature smoke checks pass within their recorded scope. Cumulative mod-addition testing and the isolated grass sort experiment are still in progress; full-pack 2,400 FPS is unproven.

The [partial cumulative native Vulkan study](benchmarks/results/cumulative-native-20261005/REPORT.md) restores the accepted vanilla scene and adds the pack one JAR at a time. ModernFix warning repair and the pinned inactive-class audit reached the world unattended on all three repeats, at 2,463 / 2,568 / 2,559 FPS. This establishes native capability only for the listed partial inputs; the complete pack remains unvalidated for that target.

## Native grass continuation

The [native grass study](benchmarks/results/cumulative-foundation-20261005/REPORT.md) preserves the original private Grassier Grass JAR and its visual settings. Restoring Forge's draw callback and converted shader matrices made the grass visible through Vulkan. The sole-addon 1.0.1 ABBAAB comparison measured median 1,202.96 to 1,611.06 FPS (+33.92%), 1% low 410.26 to 454.47 FPS, and p99 2.2736 to 2.0179 ms. This is a partial-scene gain, below the requested target; one enabled run still contained a 10.45 ms frame interval. All raw frame intervals and inputs are retained.

Six native grass play checks passed: resource reload, original mesh/shader state, advancing animation state, movement trails, Nether entry and Overworld return. Reviewed close-ups show grass geometry. All styles, Oculus/Iris interoperation, full Noxviola and zero hitching remain unaccepted. Embeddium/Zink remain excluded by the user. The 1.0.3 owner-drain invalidation experiment and `.10` pending terrain-build experiment have explicit tests and their own runtime evaluation; neither is inferred successful from source tests.
