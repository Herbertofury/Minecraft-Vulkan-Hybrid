# Partial cumulative native Vulkan measurements

The isolated cumulative profile launches into the original checksum-verified vanilla scene. Integrated Hari retains 2,400+ FPS capability with ModernFix present: the repaired ModernFix runs measured 2,463.26, 2,568.25 and 2,559.49 FPS. Every listed capture reported `api_renderer=VULKAN`; no forced gate or shader capability spoof was used. These are partial-pack measurements, not a completed Noxviola result.

| Stage | Median FPS | Median 1% low | Median p99 ms | Java to scene s | Java RSS MiB |
|---|---:|---:|---:|---:|---:|
| 000-accepted-vanilla-base | 2391.11 | 518.44 | 1.1470 | 23.09 | 3280.6 |
| 001-integrated-vanilla-base | 2582.94 | 575.17 | 0.7655 | 31.11 | 3960.2 |
| 001b-original-pack-config | 2527.35 | 555.42 | 0.9038 | 22.63 | 4188.6 |
| 002-modernfix-native-warning-repaired | 2559.49 | 559.04 | 0.8230 | 20.51 | 2659.2 |

Three consecutive captures per group, 60-second warmup and 30-second capture. Same original vanilla terrain/world copy and camera, 1920x1080, render distance 16, simulation distance 12, Fancy graphics, AO enabled, mipmaps 4, VSync off and unlimited FPS. Java 17, Forge 47.4.26, 8 GiB maximum heap and LWJGL stack 1024 KiB. Hardware: i9-13900K, 64 GiB RAM, RTX 4090, driver 610.88. Exact settings and mod hashes are retained per capture.

The initial group uses accepted Hari 2.4.10; the next two use integrated `.2`, first with vanilla configs then with a safe copy of the original pack configs. ModernFix uses integrated `.3` and ModernFix 5.27.83. `.3` adds only the audited inactive ModernFix class exemption; enabled/unknown effective state and changed JARs still reject Vulkan. The optional missing-performance-mod advisory is disabled only in the isolated cumulative profile, because FerriteCore has not yet been added. The two blocked attempts are retained privately and contain no accepted FPS capture.

ModernFix capture RSS is 2,381-2,913 MiB versus 3,764-4,219 MiB in the preceding config group. That is useful memory evidence, but the groups were consecutive and the integrated JAR version changed, so this is not an isolated causal speedup test. Performance-mod retention remains provisional pending matched benefit checks. No 1% low or p99 result supports a zero-hitch claim.

These groups are sequential capability checks, not an ABBAAB promotion series. Cache state, initial preparation and concurrent projects explain possible variability; no causal FPS gain is claimed. GameSync and AoA/Enderloom remained active. Per-second whole-adapter GPU counters include their work and cannot attribute utilization to Minecraft. The earlier full-pack comparison uses different terrain and settings; it is not a controlled mod-only delta from this scene.

All listed runs reached the world automatically, saved and exited normally. Scope is one stationary scene with the listed mods. The next libraries and content mods are being added one JAR at a time; additions that select OpenGL fallback or reproduce frame-performance regressions require repair before progression. Full native Vulkan compatibility for every mod, 2,432-2,470 FPS for the complete pack, and extended playability are unfinished.
