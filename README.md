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

## Current Noxviola development

The rebuildable [Hari + DimThreads integration](integration/dimensions/README.md) now produces `.22` for Forge47.4.26. It combines the dimension engine, exception-safe ownership and queue boundaries with Hari, preserves worker limits and includes exact frustum invalidation, retained rebuild work and native stencil operations. Source/accepted releases remain immutable. Original Grassier Grass assets, geometry, density/range and settings remain intact; use the tested grass addon1.0.3 alongside the integrated Hari artifact in the disposable trial.

| Actual test | Result and scope |
| --- | --- |
| Six sole-Hari `.9/.10` native grass ABBAAB captures | Median1866.79→2617.09 FPS (+40.19%); 1% low490.58→616.75, p991.9118→0.7340ms |
| Six sole-Hari `.12/.13` native grass captures | Medians2543.55→2554.23 FPS; overlapping ranges, no new causal gain credited. Three `.13` runs2525.30/2562.84/2554.23 FPS |
| Latest minimal `.13` scene, three repeats | 4472.93/3975.93/4505.85 FPS, median4472.93; capability evidence, not unmodded Minecraft or a matched version gain |
| Native stencil and original Ponder component | All1024 pixels exact in each repaired clip/mask/disable case; old `.12` clipping rejected. Historical component scope; bounded original Create/Ponder now passes separately |
| Repaired Iris-reference native compiler and GPU resource boundary | Actual vertex/fragment/compute compilation, cache negative controls, primitive native draw and reflected192-byte UBO uploads/readback pass; active shaderpack/provider not accepted |
| Historical full-pack OpenGL candidate | Median219.39→175.22 FPS despite repeated startup166.40→152.82s; promotion rejected |

[Captures, reviewed grass screenshots and limits](benchmarks/results/cumulative-foundation-20261005/REPORT.md) retain raw numeric intervals, startup and CPU/GPU telemetry. Seven bounded native grass reload/animation/trail/FOV/dimension checks pass. Settings remain1920x1080, RD16/SD12 Fancy. Measurements are CPU RenderTick END intervals; presented-frame throughput and zero hitching are not established. Concurrent workloads remain running and contamination is recorded.

FerriteCore is retained for measured7.60% live-managed-heap reduction. EntityCulling FPS ranges overlap; Ixeris input-polling and C2ME scheduling benefits remain unproved. Their originals and rejected trials remain preserved, disabled only in the owned continuation. Latest official Field Guide1.20.4 removes the older recipe error and retains native capability.

Cumulative progression originally paused at Create6.0.8 with135 unsupported Flywheel references across116 APIs. The new [real native Flywheel backend](compat/native-flywheel-backend/README.md) replaces the legacy renderer while preserving original public APIs/libraries/assets. Original powered motors/shafts/fans, reload recovery, original Ponder playback/seeking, config UI color copies/stencil and GPU culling now pass eleven actual Minecraft checks. Complete contraptions/blueprints/material parity and active Oculus/Iris remain unfinished; the cumulative pack is not promoted from this bounded test. [Shader compiler/resource status](compat/vulkan-shaders/README.md) distinguishes tested native boundaries from complete provider support. Full Noxviola at2432–2470+FPS, all grass styles, startup/hitch acceptance and shader parity remain unfinished. Embeddium/Zink stay excluded. Personal Noxviola and GameSync/AoA/Enderloom remain untouched.

The Desktop development artifact has valid resource metadata (`pack_format:15`), checksum receipts and no automatic diagnostic controller. Install/adopt only after complete isolated validation; no personal-pack promotion, main merge or release has occurred.

The current native continuation ships original Flywheel compute/indexed-instancing components in Hari15 and adds original Ponder clipping of original Create shaft content in Hari16. All ten completeGPU image cases pass. The current six-run grass13/15 comparison has medians2508.92/2604.96 FPS; earlier15 misses remain retained and no speedup is attributed to the unused Flywheel component. [Source, original licenses, exact evidence and remaining backend/provider work](compat/native-flywheel/README.md). Full Noxviola/activeshaderpack/startup/zero-hitch goals remain unfinished.

Hari `.22` advances the earlier `.16` component to original Create's actual entrypoint and native Engine. [Exact eleven-check runtime/GPU/source/artifact proof](benchmarks/results/cumulative-foundation-20261005/proofs/native-create-engine-v22-runtime.json) and [rebuild/private packaging instructions](compat/native-flywheel-backend/README.md) are available. Six framebuffer cases each match512 pixels, actual cull/indirect visibility16 ->0 ->16 returns intact and the owned profile's original SHA map is restored. These diagnostics make no FPS/startup/full-pack/zero-hitch claim. A separate matched powered-machinery comparison is being recorded; original binaries, failed evidence and the accepted source remain preserved.
