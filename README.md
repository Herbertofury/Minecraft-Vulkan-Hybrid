# Minecraft Vulkan Hybrid

Canonical repository for **Minecraft Vulkan Hybrid / HariMultiThread**, Minecraft **1.20.1** and **Java 17**. The immutable accepted 2.4.10 source targets Forge **47.4.23**; the rebuildable 2.4.11 dimension integration targets latest Forge **47.4.26**. Migration is prepared on `migration/projectdump-20261004`; `main` remains unchanged until review.

The complete merged Java/Gradle project, shaders, assets and upstream licenses are in [`source/`](source/). The accepted production JAR SHA256 is `8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f`. The renderer is included; do not add a separate VulkanMod JAR to this instance.

| Directory | Contents |
| --- | --- |
| `integration/dimensions/` | Rebuildable Hari + DimThreads artifact, pinned upstream source, ownership fixes and shared simulation budget |
| `compat/` | GeckoLib correctness repairs, optional Field Guide cache, native interoperability and historical renderer research |
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

Use the isolated Modrinth benchmark instances and [matched protocol](benchmarks/PROTOCOL.md). The existing Linux Mesa software-driver results are historical evidence. [Completed Windows/RTX 4090 evidence](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/windows-20261004/REPORT.md) includes six matched real Minecraft runs and a separate GPU readback/profile. Median FPS was 2,432.29 ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ 2,470.06 (+1.55%), 1% low 440.41 ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ 503.56 (+14.34%), while median p99 worsened 0.8864 ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ 1.0483 ms. Run ranges overlap and other GPU workloads continued, so no reliable new FPS gain or performance promotion is claimed. Both isolated Modrinth instances use latest official Forge 47.4.26; source acceptance/build remains pinned to 47.4.23. [Windows launch requirements](docs/BUILD.md) include the required pre-launch LWJGL stack setting.

Ctrl+F9 records 30 seconds of real frame intervals after warmup. Preserve each `harimt-fps-last.json`. Compare average FPS, 1% low, p95/p99 frametimes, actual rendering, and clean save/restart, with identical world copies/settings/mods and balanced alternating runs. Never stop GameSync, AoA, Enderloom or other workloads for a cleaner number; record overlap.

[Canonical wiki](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/wiki) Ãƒâ€šÃ‚Â· [Original artifact folder, unchanged access](https://drive.google.com/drive/folders/16zuPwFiiKZ5ewPIE11keigFUaJvsd717) Ãƒâ€šÃ‚Â· [Full provenance and scope](docs/MIGRATION.md)

## Current Noxviola development

The rebuildable [Hari + DimThreads integration](integration/dimensions/README.md) produces `.23` for Java17 / Minecraft1.20.1 / Forge47.4.26. One artifact contains the exception-safe dimension scheduler and native Vulkan renderer. Accepted original source/releases remain immutable; Embeddium and Zink remain excluded.

The [real native Flywheel Engine](compat/native-flywheel-backend/README.md) runs original Create6.0.8, retaining original APIs, libraries, assets and visual plans. Fourteen bounded actual machinery/reload/Ponder/config/GPU/rotating-bearing/disassembly/breaking checks pass. A further small-batch prototype passes15 feature checks and53,771 packing controls, but its performance comparison was deferred when the user prioritized the complete pack. It is not a performance promotion or a replacement for original Create in the full OpenGL fallback pack.

| Actual matched measurement | Result and scope |
| --- | --- |
| Native Create light cache, six ABBAAB | Median1755.43 ->2024.66FPS (+15.34%), lows403.53 ->465.59; retained scoped gain |
| Native grass helper3/5, six ABBAAB | Median1726.62 ->1926.61FPS (+11.58%); first pair nearly equal/later controls fall; bounded native scene only |
| Descriptor batching / grass first-build7 | -4.26% / -9.48%; rejected and preserved with original failures and controls |
| Earlier sole-Hari native grass9/10 | Median1866.79 ->2617.09FPS (+40.19%); target capability in that smaller scene |
| Complete352-JAR startup A/B/A/B | Repeated game-to-world161.67 ->137.08s (-15.21%); FPS ranges overlap/final pair regresses; lazy-model option rejected |

[Native captures and source/artifact/control proofs](benchmarks/results/cumulative-foundation-20261005/REPORT.md) and [complete-pack repairs, captures and startup limits](benchmarks/results/fullpack-20261005/REPORT.md) distinguish each scene/mod set. The earlier2000+ native values and minimal4472.93FPS capability do not establish the full pack's performance. Measurements are Forge RenderTick END CPU wall intervals, not presented throughput. Identical copied worlds, camera, visual settings and mod sets are required for each comparison; unrelated workloads continue and contamination is recorded.

Before the native-only instruction, the complete pack retained original gameplay content and passed seven bounded reload/original Physics octopus spawn-death/server-client dimension round-trip checks after Oculus memory-copy, EnhancedCelestials optional-renderer metadata and Physics actual-provider detection repairs. GeckoBetterFPS is disabled because its absent Sodium API broke12 constructors; original GeckoLib remains. That historical gate selected **OpenGL fallback** because native renderer/provider/gameplay interoperability was unfinished. The current native-required candidate blocks instead of falling back. [Shader/provider source, native primitive proofs and remaining implementation](compat/vulkan-shaders/README.md). Full native Noxviola, active shaderpack parity,1500/2400FPS and zero hitches are unaccepted.

Per the latest user request, all 21 auxiliary performance mods are now recoverably disabled in the isolated full candidate, including ModernFix, FerriteCore, C2ME, EntityCulling, Kerria and our separate grass and Field Guide query helpers. Original Grassier Grass, gameplay/library/world-generation features and required correctness/interop fixes stay. [New baseline, exact removal decisions and actual results](benchmarks/results/fullpack-20261005/no-aux-performance/REPORT.md). No optimizer is automatically re-added from a different scene or prior memory/startup result.

The organized Desktop project is `C:\Users\Owner\Desktop\Minecraft Vulkan Hybrid`; development JARs have valid format15 metadata, checksums and original notices. The isolated Modrinth candidate is separate from personal Noxviola, with its automated controller disabled between tests. Build/launch instructions are linked above. Personal worlds/accounts/saved profiles and GameSync/AoA/Enderloom remain untouched. No main merge, release or personal-pack promotion occurred. Migration preserves1001 source files and402 relevant commits across43 filtered branches. Source wiki routing can be overwritten by its hourly mirror; ProjectDump PR30 remains separately awaiting merge authorization.

The latest instruction explicitly removes Oculus and Indigo and requires native Vulkan. The current 327-JAR isolated candidate has 21 optimizers disabled, retains 44 FFAPI modules, stages the previously validated native Create14 backend and installs Hari23 with a tested native-required policy. Preflight has cleared renderer conflicts but still blocks669 untranslated references/360 APIs across22 archives; no new OpenGL game was launched. This is not a ready1500/2400FPS native full pack. See the linked current baseline report for exact hashes and remaining work.
