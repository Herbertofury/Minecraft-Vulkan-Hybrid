# HariMultiThread Ultimate 2.4.10 Vulkan Hybrid

Minecraft 1.20.1 · Forge 47.4.23 · Java 17

## What changed

Animated texture uploads stage the exact source span the Vulkan copy reads. Leading skipped rows, unused image frames and trailing bytes stay out of staging memory. Every requested pixel row, mip level, animation update, image transition and transfer write barrier is retained. Source positions are preserved and bounds are checked before native copying. No graphics, animation, view-distance or simulation settings change.

Ctrl+F9 records five seconds of warmup followed by thirty seconds of real world frame intervals. The report shows average FPS, 1% lows, p50/p95/p99 and maximum frame time, sample count, graphics/simulation settings, heap and GC observations. It writes `harimt-fps-last.json` in the Minecraft instance directory. Keep prior reports before starting another capture. A menu, reload, dimension change or pacing/settings change marks the sample invalid rather than quietly discarding inconvenient frames. Ordinary gameplay does not sample clocks or allocate frame arrays when capture is idle.

## Measuring on your PC

1. Use your regular world, mods and graphics settings. Leave texture animations and your usual visual effects enabled.
2. For a throughput comparison, use unlimited FPS with VSync off on both builds. Keep your normal pacing settings when measuring how your everyday game feels.
3. Load the same scene and let chunks and shaders settle. Keep the same camera, window size, render/simulation distances, resource packs and shader pack between compared runs.
4. Press Ctrl+F9. Wait for the saved-report message, then retain the JSON. Run at least three captures per build and alternate the order.
5. Compare average FPS together with 1% lows and p95/p99 frame times. Higher FPS with worse spikes does not satisfy the acceptance contract.

Average FPS is 1000 divided by the arithmetic mean of all frame times. The 1% low is 1000 divided by the mean of the slowest ceil(1% of samples) frame times. Timing includes pacing and driver waits; it does not claim to measure GPU execution time. No frame samples are silently dropped.

## Verification

The binary is byte-for-byte reproducible. The exact complete source has 1001 files and 24 pinned reconstruction steps. The compiled measurement baseline retains every one of the accepted 2.4.9's 703 existing class files unchanged and uses the identical new FPS-capture class as the candidate.

The focused copy test passed 800 source layouts and used actual native libc memcpy. The animation-sheet fixture copies 16384 bytes instead of 524288 bytes per update. This component result is separate from Minecraft FPS. Actual Vulkan readback covers RGBA and R8, three mip levels, overlapping writes, row padding and nonzero buffer positions.

Final A/B results and native compatibility receipts are supplied with the release proof index. Performance runs use the same host, complete restored world, mods, settings, resolution, camera and packs, thirty seconds of warmup for the animation fixture, sixty seconds for stock confirmation, and thirty seconds of all frame samples. Vulkan validation is omitted equally from both performance variants. Separate correctness runs retain the complete pinned Khronos validation layer and synchronization checks.

Compatibility coverage includes Aether, Midnight, original C2ME Forge 9.6 and 9.8, original portals and mob AI, chunk unload/return, NBT saves and a second JVM, resource reload, window resize, OptiFine, Distant Horizons, Rubidium, Oculus/Embeddium with the original enabled MakeUp shader pack, and the other established renderer profiles.

Tests run on Linux Mesa software graphics. Hardware GPU FPS, Windows/macOS native execution, arbitrary modpacks and every boss combat phase require their own measurements. The Ctrl+F9 report supports direct measurements on the user's real GPU. Renderer/shader mods that require OpenGL retain the established OpenGL compatibility path.

## Source and upstream review

The complete accepted 2.4.9 repair floor is preserved. Existing exception propagation, C2ME light-ticket ownership, entity AI and collision work are unchanged. The new work concerns texture staging and opt-in measurement.

The exact-source-span calculation was checked against current primary VulkanMod work, including [upstream PR 872](https://github.com/xCollateral/VulkanMod/pull/872). That unmerged newer-version patch is not imported wholesale: the 1.20.1 hybrid already normalizes GL's default row length, and this change preserves its existing API and queue ownership. The bounded source span also prevents the old whole-buffer read from extending beyond a shifted buffer's remaining bytes.

## Measured release results

The identical 8x animation fixture measured 17.2760 to 20.4235 average FPS (+18.22%) and 10.8350 to 13.4946 1% lows (+24.55%). p95/p99 frame times improved 23.59%/22.01%. Process CPU per frame fell 10.67% and peak RSS fell 11.89%. This is a Linux software-renderer stress scene, not a universal hardware FPS prediction. The fixture is not shipped as a default resource pack.

The twelve-trial stock confirmation measured Vulkan average FPS −0.99%, 1% lows +1.51%, p99 −1.07%; GL average FPS +0.38%, 1% lows +5.97%, p99 −2.72%. The initial short stock tail flags and every original raw frame remain retained. GL-control gains are not attributed to the Vulkan texture patch.

An initial stock peak-RSS alert (+12.63%) remains in the proof. Independent native-memory tracking and actual OS mappings showed substantial adaptive G1 heap commitment/residency variation. A repeated adaptive-heap test measured peak RSS −6.81%, live heap +0.20%, native committed memory −2.15%, and non-heap resident memory +1.98%. Identical pre-touched fixed heaps measured peak RSS +0.15%, live heap −1.11%, native committed memory +0.44%, and non-heap RSS +1.53%. These results support heap variability rather than intrinsic texture allocation growth; the original alert did not have NMT instrumentation. The 5% material threshold was preserved, and no original results were removed. Diagnostic collection ran after each full frame capture. Release JVM settings remain unchanged.
