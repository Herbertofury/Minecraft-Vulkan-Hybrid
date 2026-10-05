# Cumulative foundation and Vulkan translation diagnostics

The original vanilla scene is retained at 1920x1080, render distance 16, simulation distance 12, Fancy/AO/mipmaps 4, unlimited FPS and VSync off. Forge 47.4.26, Java 17, 8 GiB heap, i9-13900K/64 GiB/RTX 4090 driver 610.88. Sixty-second warmup and thirty-second capture; all CPU frame intervals retained. These are partial foundation additions, not all 350 pack additions or a complete pack acceptance.

| Added stage | Captures | Median FPS | Minecraft API |
|---|---:|---:|---|
| 003-architectury | 3 | 2452.91 | VULKAN |
| 004-cloth_config | 1 | 2433.01 | VULKAN |
| 005-geckolib | 1 | 2542.73 | VULKAN |
| 006-citadel | 3 | 2405.96 | VULKAN |
| 007-curios | 1 | 2499.13 | VULKAN |
| 008-embeddium | 1 | 2418.17 | OPENGL_FALLBACK |

The first Architectury capture overlapped preparation and measured 2,096.67 FPS; it remains in the dataset. Subsequent captures were 2,452.91 and 2,597.64. Single-capture rows are capability checks, with limited confidence. Embeddium selected NVIDIA OpenGL and measured 2,418.17 FPS; additions paused at this renderer transition. The user then explicitly excluded Embeddium and Zink from the current native continuation. No gate was forced and no required renderer feature was removed.

The Citadel-only native ABBAAB comparison holds every other foundation JAR constant, including Curios. The absent/present medians are 2319.61/2490.04 FPS with overlapping trial ranges. The earlier apparent Citadel drop did not reproduce; this is not evidence that the library causes a speedup. Citadel remains required. An interrupted third attempt had no accepted capture and was preserved privately; its fresh retry supplies the third valid B trial.

The Zink diagnostic recorded 1,757.19 FPS (1% low 463.53, p99 1.8134 ms) and produced a JFR on normal exit. Renderer identification confirms Mesa Zink Vulkan 1.4 on the RTX 4090. Valid Windows counters for this Java PID report a 66.15% median 3D-engine utilization over the capture; engine percentages are not added together. Whole-adapter CSVs include GameSync and AoA/Enderloom, which were left active. JFR native and execution sample counts use different sampling mechanisms; they are not wall-time percentages. The JFR thread named `main` is verified through its Minecraft/GLFW stack and is the render thread. Samples identify presentation and redundant palette lock bookkeeping; they do not by themselves establish a causal FPS gain.

Two class-export diagnostics measured 1,596.52 FPS with `.3` and 1,837.61 FPS with `.4`. These are instrumented capability checks, not a matched speedup claim. The actual transformed `.4` class retains synchronized palette getters, mutation, snapshot and renderer accessors; its redundant read/write-lock field is absent. The original Minecraft class bytes and raw JFR remain private. The user stopped the Zink comparison after three completed captures (one `.3`, two `.4`) and requested native continuation without Embeddium or Zink. Those three captures are not a completed matched series and establish no promotion decision. The `.4` palette change remains experimental; the active native continuation returns to `.3`.

All four native WGL probes passed required functions, RGBA readback and swap-interval changes at 256x256 and 1920x1080, using Mesa and system GDI swap routes. The game-size interval-zero groups took about 49-57 ms per 48 presents, while interval one took about 382-383 ms. Neither route reproduced a fixed 144 FPS limit. These probes are not Minecraft FPS and do not establish a game presentation fix.

Inputs, mod hashes, aggregate config digest, unchanged settings, CPU/RSS, GPU counters and every frame interval are retained. No private configs, accounts, raw launcher/game logs, copyrighted repaired assets or Minecraft bytecode are published. Cache state and simultaneous projects limit causal confidence. Full-pack native compatibility, the 2,432-2,470 FPS target, long play tests and zero-hitch capability are still unfinished.

## Native continuation after renderer exclusions

ImmediatelyFast selected NVIDIA OpenGL and measured 759.71 FPS; it was rejected for this native performance profile. With it removed, three unchanged native captures measured 2,487.43 / 2,600.08 / 2,571.10 FPS (median 2,571.10), with 1% lows 546.26 / 572.41 / 573.79 FPS. One launch overlapped packaging during startup/warmup; it is retained and does not establish a cold-start speedup. The corrected Oculus vertex-memory call then reached the scene without Embeddium, but selected NVIDIA OpenGL and measured 513.61 FPS. This proves the narrow startup repair, not native shader support.

The user subsequently excluded Oculus and requested the VulkanMod-supported shader option, deferring Oculus interoperability until the pack works. The Oculus-free native verification measured 2,432.67 FPS, 1% low 514.72 FPS, and repeated-cache game-to-world startup 21.54 seconds. Every listed completed trial saved and exited normally. Oculus, Embeddium and Zink are excluded from the isolated cumulative profile; the original personal pack remains preserved. ImmediatelyFast is not credited with a native benefit. The verified native foundation supplies the next mod's comparison.

VulkanMod recommends Beryl. Its 16 published artifacts have no Forge 1.20.1 build; current metadata marks its license All Rights Reserved and provides no source URL. No incompatible artifact is installed or declared working. See the [shader compatibility record](../../../compat/vulkan-shaders/README.md). Native shaders, all remaining pack additions, full-play validation and the full-pack FPS target remain unfinished. Historical rejected measurements stay in this dataset.

## Visible native grass and shader scope

The user restored Oculus/Iris compatibility to the required goal. They are deferred while the native grass path is validated, rather than permanently excluded. Embeddium and Zink remain excluded by the user. Beryl has no published Forge 1.20.1 artifact. No active GL compute or shader call is hidden by the reachability audit.

The pinned original Grassier Grass JAR first selected NVIDIA OpenGL and measured 327.33 FPS. Native prototypes `.5` and `.6` failed shader compilation. `.7` (2,348.56 / 2,401.31 / 2,388.01 FPS) and `.8` (2,616.39 FPS) are rejected because Hari's terrain replacement skipped Forge's draw callback, omitting the grass geometry. Those figures are not valid performance successes.

`.9` restores the exact Forge 47.4.26 callback and custom shader matrices. The original grass now appears in an actual native Vulkan scene, measuring 1,221.19 FPS, 1% low 432.29 FPS, p99 2.2231 ms and repeated-cache game-to-world startup 23.27 seconds. A separate JFR diagnostic reproduced 1,222.95 FPS but is excluded from matched comparisons. These builds and renderer settings differ from the earlier OpenGL trial, so the difference is not a same-build causal speedup estimate. The original grass JAR, density, range, textures, animation uniforms and visual settings remain intact. Wind/trail/style/dimension/reload parity still needs extended validation.

The native JFR identifies repeated grass section priority sorting on the render thread. Sample counts from native and Java samplers are not wall-time percentages. The 1.0.0 exact-distance addon was the sole toggled JAR in six native ABBAAB trials: absent/present medians 1,215.46/1,307.87 FPS (+7.60%), but 1% lows 405.99/380.59 FPS and p99 2.2301/2.2889 ms worsened. It does not satisfy the target or no-hitch objective, and is not promoted. Cache-warm startup samples vary, with one missing ModernFix time marker retained as null rather than inferred. The original grass geometry remains visible in inspected native screenshots.

Development 1.0.1 retains the exact permutation only for identical section prefixes and raw camera values, separately for each sort site. Any changed input/camera or failed sort invalidates reuse. Independent JDK ordering parity passes 30,000 checks and three deliberately unsafe variants fail. This source-built candidate has valid pack metadata and is undergoing its own native matched comparison; no success is inferred from tests. Whole-adapter GPU telemetry includes the other projects, which remain active. Full Noxviola and shader-pack acceptance remains unfinished.
