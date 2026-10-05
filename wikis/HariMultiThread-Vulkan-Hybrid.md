# Minecraft Vulkan Hybrid

Canonical [repository](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid) and [draft migration PR #1](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/pull/1). Complete accepted source/history/assets/licensing are preserved on the feature branch; no merge or release occurred. [Current build](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/integration/dimensions/README.md), [native grass data](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/blob/migration/projectdump-20261004/benchmarks/results/cumulative-foundation-20261005/REPORT.md), and [canonical status](Home) describe actual Windows validation.

Native .9/.10 six-run ABBAAB measured 1,866.79 to 2,617.09 median FPS (+40.19%), preserving original grass/settings. Current .11 native subset measured 2,529.28 / 2,590.76 / 2,502.04 FPS and passed seven bounded native checks, including stationary FOV changes. Minimal integrated .10 capability measured median 4,451.34 FPS. These CPU interval results are scoped; full Noxviola, active Oculus/Iris, all grass styles and zero hitching remain unfinished. Other projects remain active; Embeddium/Zink are excluded.

The original release record below is preserved byte for byte for provenance. Its historical Linux software-driver evidence and ProjectDump workflows are separate from current Windows results.

---

# HariMultiThread Ultimate — Vulkan hybrid

Minecraft **1.20.1 · Forge 47.4.23 · Java 17**. Accepted release: **2.4.10-noxviola.1-vulkan-hybrid**.

The reproducible 2.4.10 build adds exact Vulkan texture staging and a real Ctrl+F9 FPS capture. It passed **24 focused suites, all 13 broad native client profiles, three focused original C2ME 9.8 profiles, dedicated-server checks, native hotkey input, independent stock frame-time confirmation and native-memory investigation**. The same JAR bytes were used throughout.

## Downloads and installation

1. Replace your previous Hari/Async JAR with [HariMultiThread-Ultimate-1.20.1-2.4.10-vulkan-hybrid.jar](https://drive.google.com/file/d/1o0W5q3myKKPbSqjWEwxIwC0Jt_m0x4ST/view).
2. The Forge Vulkan renderer is included; remove a separate VulkanMod JAR. Retain the ordinary dependencies of your additional mods.
3. C2ME Forge is optional. Stock 0.2.0-forge.9.6 and 9.8 passed the recorded profiles. All third-party mod JARs retain their original bytes.
4. Renderer/shader integrations requiring GL and Fabulous graphics retain the established complete GL compatibility route. Async processing and the separate Vulkan collision backend remain available.

- [Verified release folder: binaries, source and complete original archives](https://drive.google.com/drive/folders/16zuPwFiiKZ5ewPIE11keigFUaJvsd717).
- [Complete source ZIP with licenses, 1001 hash-checked files and 24 pinned recipe steps](https://drive.google.com/file/d/1NFGVZeurxOaIEkbBvLW94gZFT7YSVs5A/view).
- [Installation and capture instructions](https://drive.google.com/file/d/1GLEMn9xyCo73tmzG6AUIfniEdMyRMczK/view).
- [Release notes, measured results and original-alert interpretation](https://drive.google.com/file/d/19Yir2agxh7BBMoARvnjnXCm8hNNQxJ6r/view).
- [Complete proof and hash index](https://drive.google.com/file/d/1jA9mSOCVw7wceV3HFH5kUHZUDalOIBdg/view).
- [Source, executable QA and accepted manifest](https://github.com/Herbertofury/Minecraft-Vulkan-Hybrid/tree/migration/projectdump-20261004).

## Measured FPS improvement

| Same-host Linux Mesa software test | Average FPS, baseline → candidate | Average change | 1% low change | p99 frame-time change |
| --- | ---: | ---: | ---: | ---: |
| Vulkan, identical 8× animated-texture fixture | 17.2760 → 20.4235 | +18.22% | +24.55% | −22.01% |
| Stock Vulkan, twelve-trial confirmation | 25.7043 → 25.4496 | −0.99% | +1.51% | −1.07% |
| Stock GL compatibility, twelve-trial confirmation | 17.9729 → 18.0418 | +0.38% | +5.97% | −2.72% |

Every full capture retains thirty seconds of all frame intervals. The animation fixture uses thirty seconds of warmup; stock confirmation uses sixty seconds and six original JVM trials per build, in balanced alternating order. World, mods, resolution, camera, resource packs and graphics/simulation settings are equivalent within each case. Validation and JFR are omitted equally from performance variants; separate native correctness tests retain full pinned Khronos validation.

The texture patch copies exactly the consumed source byte span. **Every requested pixel row, animation update, mip level, copy command, transition and transfer barrier remains.** Source positions are preserved and out-of-range reads fail before native copying. No resolution, graphics, animation or simulation setting is reduced. The fixture is a test pack, not a new default resource pack.

The animation-heavy Vulkan test also measured **−10.67% process CPU per frame and −11.89% peak RSS**. The focused real native memcpy test passed 800 source layouts and stages 16,384 instead of 524,288 bytes per fixture upload. Component copy speed and software-renderer FPS are separate measurements. GL-control gains are not attributed to this Vulkan patch.

## Stock tails and memory investigation

The initial short stock samples flagged tail latency. All original reports and raw frames remain in the archives. Independent twelve-trial stock confirmation cleared the unchanged **5% material frame-metric threshold** in both renderer routes.

One stock run also flagged +12.63% peak process RSS despite lower live heap. That original alert and the initially failed resource gate remain in the proof. Independent NMT, actual OS mappings and heap diagnostics found substantial adaptive G1 heap commitment/residency variation. Repeated adaptive-heap captures measured peak RSS −6.81%, live heap +0.20%, native committed memory −2.15% and non-heap RSS +1.98%. Identical pre-touched fixed heaps measured peak RSS +0.15%, live heap −1.11%, native committed memory +0.44% and non-heap RSS +1.53%.

The original alert was not instrumented with NMT. These subsequent results support JVM heap variability rather than intrinsic texture-allocation growth. All protected native/live-memory checks remained within the same 5% material threshold. Diagnostic GC and memory queries occurred **after** each complete frame capture; no slow frames or original GC pauses were removed. Release JVM settings are unchanged.

## Measure your actual PC

Press **Ctrl+F9** in a loaded world. Five seconds of warmup precede thirty seconds of real frame intervals. The report saves to `harimt-fps-last.json` in the instance directory. Keep the previous report before another capture.

The report includes average FPS, 1% lows, p50/p95/p99/max frame time, raw intervals, settings, heap and GC observations. Average FPS is 1000 divided by mean frame time; 1% low is 1000 divided by the mean of the slowest ceil(1% of frames). A menu, resource reload, dimension or pacing/settings change invalidates the sample. Ordinary gameplay does not sample clocks or allocate capture arrays while capture is idle.

Alternate at least three runs per build with the same scene and settings. For throughput use unlimited FPS and VSync off on both builds; for everyday smoothness retain your ordinary matching pacing settings. Assess frame-time tails as well as average FPS.

## Native compatibility and identity

Aether, Midnight, original C2ME Forge 9.6/9.8, OptiFine, Distant Horizons, Rubidium, Oculus/Embeddium including enabled MakeUp-UltraFast 9.5f, ImmediatelyFast, EntityCulling, Lazurite, Continuity and the established optimized stack passed their recorded original profiles. Portals/rifts, native mob AI and whirlwind expiry, unload/return, chest NBT, reload, resize and same-world second JVM remain verified. Actual Vulkan readback matches every expected pixel for RGBA/R8 at three mip levels, with padding, nonzero positions and overlapping writes.

The compiled measurement baseline retains all 703 accepted 2.4.9 class files unchanged and shares byte-identical FPS instrumentation with the candidate. Only VulkanImage and StagingBuffer source behavior changes; their two nested class instruction streams remain unchanged. Other binary differences are release version metadata. Existing error propagation, C2ME ticket/queue ownership and collision work are retained.

| Identity | Value |
| --- | --- |
| JAR bytes | 30,763,983 |
| JAR SHA256 | `8b593bac1ac77670849ed992c808d327dd6d9f188ddfdea341ac88f16edf8c9f` |
| Product source | `edc2d2a04b2dd7b48b71eb26ad7bb32aca2f8ad2` |
| Final release/QA checkpoint | `89bf5c4027137a576e71f24736a8a3f1f4f25149` |
| Compile / broad native / C2ME 9.8 runs | 37171853240 / 37191427463 / 37191427447 |
| FPS / stock confirmation / memory runs | 37191427448 / 37192813688 / 37194598352 |
| Complete source SHA256 | `8fcf93eceeda711370d130feda75b102dac07bee64169b5e70144e1e8645511e` |

Complete binaries, source, native archives, all FPS trials, profiling and original pre-startup launcher failures are saved with verified raw readback hashes and ZIP entry integrity. Upstream work was checked against the existing pinned 1.20.1 integration; the newer unmerged [VulkanMod PR 872](https://github.com/xCollateral/VulkanMod/pull/872) was not imported wholesale.

**Scope:** native tests use Linux Mesa software Vulkan/OpenGL. Hardware GPU FPS, Windows/macOS native execution, arbitrary packs and every boss combat phase require their own tests. Rubidium's upstream Lucent dynamic-lighting restriction is not claimed repaired. The preserved previous release record below contains the established renderer contract and historical repairs.

---

## Previous accepted 2.4.9 release and earlier history

# HariMultiThread Ultimate — Vulkan hybrid

Minecraft **1.20.1 · Forge 47.4.23 · Java 17**. Accepted release: **2.4.9-noxviola.1-vulkan-hybrid**.

Hari’s parallel entity work and the Forge Vulkan renderer are merged into one mod. The exact reproducible JAR passed **13 native client profiles, three additional original C2ME 9.8 profiles, dedicated-server and packaged-production checks, and 23 focused regression suites**. All completed test artifacts and source are backed up with verified raw download hashes.

## Downloads and installation

1. Use Minecraft 1.20.1, Forge 47.4.23 and Java 17.
2. Replace the previous Hari/Async JAR with [HariMultiThread-Ultimate-1.20.1-2.4.9-vulkan-hybrid.jar](https://drive.google.com/file/d/1Kpk-RaWGtc8O1Da3J0xeuLP_g9xw4fmg/view).
3. The Forge Vulkan renderer is included; remove a separate VulkanMod JAR. Keep the normal dependencies of additional mods.
4. C2ME Forge is optional. The broad matrix uses stock [0.2.0-forge.9.6](https://www.curseforge.com/minecraft/mc-mods/concurrent-chunk-management-engine-forge/files/8929972). Stock [0.2.0-forge.9.8](https://www.curseforge.com/minecraft/mc-mods/concurrent-chunk-management-engine-forge/files/9040801) has its own three focused proofs below. Third-party JARs remain unchanged.

- [Verified release folder](https://drive.google.com/drive/folders/1TZCi84twLpXuRPULJ0rvxTSdfcvt91W-).
- [Complete source ZIP](https://drive.google.com/file/d/1-EqZEtEE5MEkvtzplkSliUEl8zPKUTup/view).
- [Installation notes](https://drive.google.com/file/d/1k1Ls3lXClvHP1d8YeJTo4t-KPH0VJmGu/view).
- [Complete test and download hash index](https://drive.google.com/file/d/1PASbnvio3werzVKfZR9wDNb_g_ewVssZ/view).

JAR size: **30,750,839 bytes**. SHA-256:

```text
c1082c8282ff40221c0ea982ce329ec6ab9a26c80790d643d1277b412b8fb396
```

The Drive links retain existing project sharing permissions.

## Correctness and performance work

Original C2ME LIGHT ticket addition and removal now use the same level after the actual provider Mixin merges. The repair recognizes real Forge/SRG and OptiFine release implementations and retains original owner queues, removal operations and exceptions. Both original C2ME versions pass actual clean client shutdown and saved-world reopen.

Duplicate-entity exception cancellation is removed. Deliberate entity and client-tick faults retain original crash reports and recoverable saves. Rubidium cleanup/acquire/invalidate ownership is checked from the actual running classes in both JVMs. Native mob AI, original whirlwind expiry, chunk ownership, shader resources, Vulkan lifetime and swapchain handling remain intact.

Pooled chunk work and batched upload regions reduce queue work and allocation. The equivalent-work benchmark processes **5,120,000 items per sample**: median component time changes from **60.42 ms to 14.17 ms** (about 76.6% less), and allocation from **123,360,000 to 21,120,000 bytes** (about 82.9% less). **128 disjoint regions use one copy command**, retaining both required graphics-to-transfer and transfer-to-graphics dependencies. These are component measurements, separate from hardware Minecraft FPS.

## Rendering compatibility

Compatible profiles use the merged Vulkan renderer. External renderer/shader integrations and Fabulous graphics select the complete OpenGL route before Vulkan renderer initialization. Hari async processing and the separate Vulkan collision backend remain available.

The external GL contract recognizes **111 exact overloads**. Unsupported calls, including **2,248 reviewed unsupported overloads**, select full OpenGL; generic shader/compute placeholders do not advertise fake support. Arbitrary OpenGL programs are not universally translated into Vulkan. This allows the tested original Oculus/Embeddium, MakeUp shader, OptiFine, Rubidium and other renderer profiles to retain their original features.

## Exact-candidate native proofs

Every accepted client profile uses an installed `forgeclient`, original first-JVM dimension portals, original mob AI, four actual scene views, 49 loaded home/far chunks, actual unload/return, retained chest NBT, resource reload, real resizing, a clean save and a distinct same-world second JVM. Each JVM records **300 frames after 120 warmup frames at 1280 × 720, render distance four**.

| Original profile | C2ME Forge | Renderer | Exact passing job |
|---|---|---|---|
| aether | Absent | Vulkan | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036664) |
| oculus-embeddium | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036652) |
| optimized-stack | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036828) |
| rubidium | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036680) |
| lazurite | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036657) |
| entityculling | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036686) |
| optifine | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036671) |
| immediatelyfast | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036733) |
| continuity | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036692) |
| distanthorizons | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37165635433/job/111327879276) |
| oculus-shaders | 9.6 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37166903303/job/111331590303) |
| midnight | Absent | Vulkan | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37168634595/job/111336814178) |
| dimensions-c2me | 9.6 | Vulkan | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37168634595/job/111336814285) |

Additional original C2ME 9.8 proofs:

| Original profile | C2ME Forge | Renderer | Exact passing job |
|---|---|---|---|
| optifine | 9.8 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668733/job/111325035935) |
| distanthorizons | 9.8 | OpenGL compatibility | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37165635449/job/111327837239) |
| dimensions-c2me | 9.8 | Vulkan | [PASS](https://github.com/Herbertofury/ProjectDump/actions/runs/37168634593/job/111336814171) |

The dedicated Aether/Midnight dimension proof passed in [job 111325036397](https://github.com/Herbertofury/ProjectDump/actions/runs/37164668802/job/111325036397). Standard dedicated and packaged-production gates passed in [37163922589](https://github.com/Herbertofury/ProjectDump/actions/runs/37163922589) and [37163922551](https://github.com/Herbertofury/ProjectDump/actions/runs/37163922551). The reproducible build and 23 regression suites passed in [37163922566](https://github.com/Herbertofury/ProjectDump/actions/runs/37163922566).

MakeUp-UltraFast 9.5f remains enabled through both original realms and resource reloads in the shader profile. Actual native window captures show textured Aether and Midnight entities, lighting effects and shadows. Acceptance covers the original spawn catalogue and persistence paths; every boss combat phase is not claimed.

Earlier failed workflow runs remain failed. The table accepts individually passing jobs on this exact JAR, with each full immutable artifact independently downloaded and reconciled. No acceptance is transferred from an older binary or another C2ME version.

## QA timing recovery and scope

The final Midnight and combined-realm Vulkan jobs use official **Khronos SDK 1.4.363.0**, checksum-pinned to the September 29 release. The actual loaded validation library is verified from each original client JVM. **Core and full synchronization validation stay enabled**, including queue-submit checks. Updating the old 1.3.275 validation tooling resolved native software-input starvation without changing the product JAR. Original double-Space flight succeeds through actual key events; no flight timer or game-state injection is used.

Slow software input preparation temporarily resizes the real window to 480 × 270; 1280 × 720 is restored before every scene gate. All frame samples and terrain/mob captures retain the required full resolution. No NoAI, forced respawn, original portal bypass, shader disabling, ticket clearing, or tick/shutdown suppression is used.

Native scope is **Linux Mesa software Vulkan/OpenGL**. Windows/macOS GPU behavior, hardware FPS and arbitrary modpacks remain unmeasured. The broad matrix uses C2ME 9.6; the three 9.8 profiles do not claim a separate full 13-profile matrix. Upstream Rubidium/Lucent dynamic-lighting restrictions remain documented.

## Source and complete backups

Product source: [3bd2621](https://github.com/Herbertofury/ProjectDump/tree/3bd2621048e166cf24c8a782c79edc77b8b5b2ab/minecraft/async-1.20.1-ultimate). The complete merged archive contains **998 source files reconstructed with all 23 pinned steps**, executable Gradle wrappers, overlays, focused regressions and original licenses.

From the source ZIP:

```sh
cd source
chmod +x gradlew
./gradlew --no-daemon clean :forge:build --stacktrace
```

On Windows use `gradlew.bat --no-daemon clean :forge:build --stacktrace`.

The pinned upstreams are [JustHari01/HariMultiThread at f381611](https://github.com/JustHari01/HariMultiThread/tree/f381611c2d71a85192e2028f9e30c03823a6482b) and [kzktor/Forgified-VulkanMod at 0ceac5d](https://github.com/kzktor/Forgified-VulkanMod/tree/0ceac5d47f84c910d2f5f6d007b0ffe266c6f736).

[Complete project backup folder](https://drive.google.com/drive/folders/1vq6hWD_QqVdxMlgYrupWbhVdlNDa8K-B) contains eight byte-verified native proof volumes covering all **16 client artifacts plus the dedicated dimension artifact**, the full standard native proof, reproducible compiled checkpoint, source checkpoints and retained failed diagnostics. PROOF-INDEX.json maps every volume, artifact hash and native evidence file. Newly uploaded 2.4.9 deliverables were accepted and raw-byte verified. The previously rejected five 2.4.1 archives were not retried or rerouted.

## Previous accepted 2.4.1 record

The following frozen record documents the prior accepted binary and its separate historical delivery status.

# HariMultiThread Ultimate — Vulkan hybrid

Minecraft **1.20.1 · Forge 47.4.23 · Java 17**. Accepted release: **2.4.1-noxviola.1-vulkan-hybrid**.

Hari's parallel entity work and the Forge Vulkan renderer are merged into one mod. The tested JAR is reproducible byte for byte. Native evidence covers installed Minecraft clients and servers, strict C2ME ownership checks, actual world saves, resource reloads and Vulkan synchronization validation.

## Install

1. Use a Java 17 Minecraft 1.20.1 profile with Forge 47.4.23.
2. Replace the previous Hari JAR with [HariMultiThread-Ultimate-1.20.1-2.4.1-vulkan-hybrid.jar](https://drive.google.com/file/d/1IHdqge9h6a8OZprWP6HAdoMO3ekXqLzV/view).
3. The renderer is included in this JAR; remove a separate VulkanMod JAR from that profile.
4. C2ME is optional. The tested fixture is **C2ME Forge 0.2.0-forge.9.6**, [CurseForge file 8929972](https://www.curseforge.com/minecraft/mc-mods/concurrent-chunk-management-engine-forge/files/8929972).
5. Keep existing graphics and gameplay settings. Fabulous and incompatible external renderers retain the full OpenGL compatibility route.

The Drive files retain their existing sharing permissions. These links identify the project artifacts; they do not grant public access.

## Rendering compatibility

In Vulkan mode, Minecraft's shader, buffer, texture, render-target, draw and window paths use the merged renderer and SPIR-V pipelines. Hari's separate OpenGL terrain route respects renderer ownership and avoids probing an absent GL context.

The external-call contract recognizes **111 reviewed GL overloads by exact JVM descriptor**. It does not advertise **2,248 imported unsupported overloads**, including empty generic shader/compute placeholders. Unknown owners, overloads or unsupported APIs select the complete compatibility renderer before Vulkan starts. Arbitrary OpenGL 4.x programs are not universally translated into Vulkan.

Shader-source boundaries preserve all strings, UTF-8 byte lengths, null termination and caller buffer positions. Native LWJGL memory checks cover these paths. Buffer-based shader/program queries use the same underlying query as scalar calls.

## Correctness and performance repairs

| Area | Result |
|---|---|
| Entity scheduling | Snapshot-indexed work avoids linked-node allocation; explicit worker caps remain authoritative. |
| Shared CPU budget | Automatic entity/mesh counts leave capacity for main/render threads and external chunk engines. |
| Mesh workers | Offer, poll and wait share one monitor; close discards an already-polled task safely. |
| Vulkan uploads | Reused primitive ranges batch compatible disjoint copies while retaining overlap barriers, bounds, alignment, fences and resource lifetime. |
| Chunk visibility | Deferred graph scheduling preserves reachable visible sections across all four culling settings. |
| C2ME ownership | Chunk loads, checked RNG, trackers and spawn/removal effects use the actual owner queues; worker barriers retain managed progress. |
| Sensors | Three inherited executable decompiler failures are repaired with stable distance ordering. |
| Audio reload | Original channel clearing is ordered behind queued sound operations and completes before context teardown. |
| Errors | Tick, worker, owner and audio failures retain their original exceptions, crash reports and emergency saves. |

The equivalent-work queue benchmark measured **58.7% less queue time** and **82.9% less allocation**. A 128-region disjoint upload comparison reduced 128 copies/64 broad barriers to one copy/no barriers while preserving all 2,048 bytes. Actual native batching depends on buffer pair and ordering. These are component measurements, not a promise of overall Minecraft FPS.

## Exact-binary runtime proof

| Check | Evidence |
|---|---|
| Build, identical clean rebuild and 17 focused suites | [36899664277](https://github.com/Herbertofury/ProjectDump/actions/runs/36899664277) |
| Packaged server, strict C2ME, 256 entities, GPU/CPU switch, block NBT persistence and deliberate fault recovery | [36899664082](https://github.com/Herbertofury/ProjectDump/actions/runs/36899664082) |
| Seven installed Forge production-client scenarios | [36899664091](https://github.com/Herbertofury/ProjectDump/actions/runs/36899664091) |
| Khronos synchronization validation, active-sound reloads, far/return travel and two-worker restart | [36900474237](https://github.com/Herbertofury/ProjectDump/actions/runs/36900474237) |
| Distance eight, 169 entity-ticking chunks per destination, three sustained terrain captures and strict two-worker C2ME restart | [36906027597](https://github.com/Herbertofury/ProjectDump/actions/runs/36906027597) |

All checks used the same **30,730,495-byte** JAR with SHA-256:

```text
58ceda73c9328c78fc3d82eb652475cb4c7ddd91f3078d6d31717eb0ea9d432f
```

The expanded travel check ended with **95.1% / 96.7% central terrain coverage** after both destinations loaded. All progression captures and original logs remain preserved. Three acknowledged music sounds followed by actual resource reloads completed without audio errors. Deliberately injected tick/entity faults produced real crashes and recoverable saves.

Native tests used **Linux Mesa software drivers**. Windows/RTX 4090 FPS and arbitrary third-party modpacks remain unmeasured. The full-window finite-distance scene frontier is not a universal terrain-coverage certification.

## Source, provenance and reproduction

Product commit: [6594c6c](https://github.com/Herbertofury/ProjectDump/commit/6594c6cc652db554213c52ae02f7acd8f439644c).

The complete source contains **989 files**, including **44 legitimate builder-package files**. The immutable 23-step recipe merges:

- [JustHari01/HariMultiThread at f381611](https://github.com/JustHari01/HariMultiThread/tree/f381611c2d71a85192e2028f9e30c03823a6482b).
- [kzktor/Forgified-VulkanMod at 0ceac5d](https://github.com/kzktor/Forgified-VulkanMod/tree/0ceac5d47f84c910d2f5f6d007b0ffe266c6f736).

Original licenses and attribution remain included. The reviewed upstream cancelled-sort fix and grouped-copy/barrier principles are adapted to the existing 1.20.1 renderer; unsupported 1.21 feature requirements were not transplanted.

From the source archive's `source/` directory:

```sh
chmod +x gradlew
./gradlew --no-daemon clean :forge:build --stacktrace
```

On Windows, use `gradlew.bat --no-daemon clean :forge:build --stacktrace`.

- [Implementation, compatibility and measured results](https://github.com/Herbertofury/ProjectDump/blob/301a1856e9dba25e6a9da910617daf6905753c7e/minecraft/async-1.20.1-ultimate/VULKAN-PERFORMANCE-2.4.1.md).
- [Reproduction guide](https://github.com/Herbertofury/ProjectDump/blob/301a1856e9dba25e6a9da910617daf6905753c7e/minecraft/async-1.20.1-ultimate/REPRODUCE-2.4.1.md).
- [Frozen collection hashes and delivery receipt](https://github.com/Herbertofury/ProjectDump/blob/301a1856e9dba25e6a9da910617daf6905753c7e/minecraft/async-1.20.1-ultimate/PERFORMANCE-DELIVERY-RECEIPT.json).

The five ZIPs contain the core release, complete source, current proof and two complete historical-diagnostic volumes. Every entry is SHA-256 verified; the historical union retains all **1,168 files across 29 provider runs**. Older failures are provenance, not acceptance for the current JAR.

## Delivery status — 2026-10-02

The accepted JAR is on Drive with a verified raw readback. All five release ZIPs have been restored and match their frozen receipt byte for byte. Automatic review again rejected the source upload: it requires explicit approval for these five ZIPs and the existing Hari Drive folder and does not accept “continue” as that approval. The ZIPs remain prepared for the specific approval; no blocked upload was rerouted.
