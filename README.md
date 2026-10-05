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

Use the isolated Modrinth benchmark instances and [matched protocol](benchmarks/PROTOCOL.md). The existing Linux Mesa software-driver results are historical evidence. [Completed Windows/RTX 4090 evidence](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/windows-20261004/REPORT.md) includes six matched real Minecraft runs and a separate GPU readback/profile. Median FPS was 2,432.29 â†’ 2,470.06 (+1.55%), 1% low 440.41 â†’ 503.56 (+14.34%), while median p99 worsened 0.8864 â†’ 1.0483 ms. Run ranges overlap and other GPU workloads continued, so no reliable new FPS gain or performance promotion is claimed. Both isolated Modrinth instances use latest official Forge 47.4.26; source acceptance/build remains pinned to 47.4.23. [Windows launch requirements](docs/BUILD.md) include the required pre-launch LWJGL stack setting.

Ctrl+F9 records 30 seconds of real frame intervals after warmup. Preserve each `harimt-fps-last.json`. Compare average FPS, 1% low, p95/p99 frametimes, actual rendering, and clean save/restart, with identical world copies/settings/mods and balanced alternating runs. Never stop GameSync, AoA, Enderloom or other workloads for a cleaner number; record overlap.

[Canonical wiki](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/wiki) Â· [Original artifact folder, unchanged access](https://drive.google.com/drive/folders/16zuPwFiiKZ5ewPIE11keigFUaJvsd717) Â· [Full provenance and scope](docs/MIGRATION.md)

## Current Noxviola development

The rebuildable [Hari + DimThreads integration](integration/dimensions/README.md) produces `.22` for Java17 / Minecraft1.20.1 / Forge47.4.26. The single artifact combines the dimension scheduler, exception-safe ownership and queue boundaries with Hari and native Vulkan. Original accepted source/releases remain immutable. Original Grassier Grass geometry, density/range, assets, settings, animation and trails remain; the tested grass addon1.0.3 accompanies the accepted native milestone. Embeddium/Zink remain excluded.

The [real native Flywheel Engine](compat/native-flywheel-backend/README.md) now runs original Create6.0.8 while preserving its original public APIs/libraries/assets, content and visual plans. Fourteen actual Minecraft checks pass: original powered motors/shafts/fans, lighting snapshot reuse, reload recovery, registered Ponder playback/seeking, original config-screen color blits/stencil, actual GPU cull16→0→16 and framebuffer pixel controls, an original rotating mechanical-bearing contraption with native embedding/animation/disassembly, and the original breaking overlay. Hidden live instances retain ownership; empty deleted embeddings and unused meshes retire safely behind the native frame fence. Actual production lifecycle methods also pass eight-worker CPU controls and reject four ownership/lifetime mutations.

| Actual measurement | Result and scope |
| --- | --- |
| Original powered Create, uncached/cached light backend, six ABBAAB runs | Median1755.43→2024.66FPS (+15.34%), lows403.53→465.59, p992.2026→2.0365ms; retained scoped gain |
| Cached12/descriptor-batched13, six ABBAAB runs | Median1990.60→1905.87FPS (-4.26%), lower lows; prototype rejected and faster source restored |
| Sole-Hari `.9/.10`, original grass, six ABBAAB runs | Median1866.79→2617.09FPS (+40.19%), lows490.58→616.75, p991.9118→0.7340ms |
| Current original-grass `.13/.15` comparison | Medians2508.92/2604.96FPS; target capability retained in this scene, no gain credited to unused Flywheel component; earlier misses retained |
| Latest minimal `.13`, three repeats | Median4472.93FPS; capability check, not an unmodded Minecraft or matched version gain |
| Historical complete-pack OpenGL candidate | Median219.39→175.22FPS despite repeated startup166.40→152.82s; promotion rejected |

[All actual captures, reviewed screenshots, source/artifact hashes, CPU/GPU telemetry and limits](benchmarks/results/cumulative-foundation-20261005/REPORT.md) remain available, including failed trials. FPS measures Forge RenderTick END CPU wall intervals at1920x1080 FancyRD16/SD12,60s warmup/30s capture; presented throughput and zero hitching are unestablished. Concurrent workloads remain active and contamination is recorded. Generated renderer-decision cache bytes vary by normal timestamp/mod signature; all other config/visual/mod inputs match in the light-cache comparison. Repeated-cache startup is recorded without cold-start or causal startup credit.

FerriteCore is retained for measured7.60% live-managed-heap reduction. Latest official Field Guide1.20.4 removes the old recipe error. EntityCulling, Ixeris and C2ME purpose benefits/parity remain unresolved; their originals and rejected trials remain recoverable. [Native shader compiler/resource proofs](compat/vulkan-shaders/README.md) establish primitive SPIR-V drawing and reflected uniform uploads, with original license/source retention and mutation controls. Active Forge Oculus/Iris and complete shaderpacks remain implementation work.

Cumulative additions are still withheld from automatic promotion: the original Create entrypoint and one rotating assembly now work, but arbitrary contraptions/trains/blueprints, all light/material combinations, full OIT/depth-pyramid support and complete Noxviola remain unaccepted. Full-pack2432–2470+FPS, all grass styles, shader parity and cold startup/no-hitch goals remain unfinished. No normal-scene loss or full-pack speedup is inferred from component diagnostics.

The organized Desktop project is `C:\Users\Owner\Desktop\Minecraft Vulkan Hybrid`. Development artifacts have valid resource metadata (`pack_format:15`), exact checksums, original notices and no automatic diagnostic controller. Build/private packaging instructions are linked above. Personal Noxviola/worlds and GameSync/AoA/Enderloom remain untouched; no main merge/release or personal-pack promotion occurred. The original migration preserves1001 source files and402 relevant commits across43 filtered branches. Source wiki routing can still be overwritten by its hourly mirror; ProjectDump PR30 remains separately awaiting authorization.
