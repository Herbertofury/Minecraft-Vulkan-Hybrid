# Actual Windows Minecraft benchmark — 2026-10-04

The accepted 2.4.10 texture staging optimization ran correctly on this RTX 4090, but these measurements do **not establish a reliable FPS gain** over the instrumented 2.4.9 baseline. No speculative render change or lower visual setting was promoted. The destination remains a draft migration, with the existing accepted release preserved.

Three matched original JVM runs per version, balanced A B B A A B, restored from the same world snapshot. Both used Minecraft 1.20.1, latest official Forge 47.4.26, Java 17.0.20.1, Modrinth 0.21.6, identical control mod, 1920×1080, render distance 16, simulation distance 12, Fancy/AO/mipmaps 4, VSync off/unlimited FPS, 8 GiB maximum heap, 60-second warmup and full 30-second captures. Player/camera and weather/daytime were fixed; simulation, mobs, textures and animation remained active. Other projects/workloads were left running.

| Run-level median | 2.4.9 baseline | 2.4.10 candidate | Change |
| --- | ---: | ---: | ---: |
| Average FPS | 2432.29 | 2470.06 | +1.55% |
| 1% low FPS | 440.41 | 503.56 | +14.34% |
| p50 frame time (ms) | 0.3656 | 0.3670 | +0.38% |
| p95 frame time (ms) | 0.5389 | 0.5699 | +5.75% |
| p99 frame time (ms) | 0.8864 | 1.0483 | +18.26% |
| Maximum frame time (ms) | 9.6502 | 4.5613 | -52.73% |

Baseline FPS range 2293.57–2589.61; candidate 2382.12–2559.69. The observed higher 1% lows and lower worst spikes coexist with worse median p95/p99. The throughput ranges overlap, and one of three adjacent pairs favors the baseline. These are mixed, noisy observations, not a general performance improvement or a reason to change personal instances. A performance promotion is rejected. Neither component timings nor old Linux/software-driver numbers are substituted for this hardware result.

All six reports are complete and Vulkan; same camera/mod-helper hash/settings, no ERROR/FATAL or async overload warning in the measured windows, and clean world saves/restarts. Startup async worker waits are retained in the logs. Representative framebuffer captures show the same terrain/HUD/texture detail, with expected cloud/water animation differences. This is a short static-scene test, not full compatibility or long-session certification.

A separate original packaged Minecraft run passed real RGBA and R8 Vulkan texture upload/readback at three mip levels, including overlapping updates, padded rows and nonzero source position. Its profiling/readback overhead is excluded from the six comparative trials. The limited JFR profile points to visibility-graph traversal, queue capacity checks and frustum tests in the render thread; the six-neighbor queue reservation is already batched. Removing/caching traversal without complete invalidation checks could change visibility, so no such change was made. Startup LWJGL memory-stack exhaustion was fixed with the documented pre-launch stack setting in both variants, not presented as a numerical FPS boost.

Raw full frame samples are preserved losslessly in each `fps.json.gz`, together with input hashes, settings, total GPU telemetry and sanitized game logs. Full screenshots, raw process snapshots, pristine world and rejected pilots remain in the desktop `benchmarks/` directory; process inventories and raw JFR are kept private. JFR JVM argument/property/environment recording was disabled and their recorded counts are zero. No driver, OS/security setting, personal world, existing profile, GameSync or AoA/Enderloom process was altered.

[Protocol](../../PROTOCOL.md) · [Machine-readable results](SUMMARY.json) · [GPU readback](GPU-TEXTURE-READBACK.json) · [Thread profile](THREAD-PROFILE.json)
