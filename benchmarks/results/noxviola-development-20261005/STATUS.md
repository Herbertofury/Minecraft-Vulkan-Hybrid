# Noxviola development verification

The complete live Noxviola profile has not been modified. Tests use task-owned Modrinth clones, disposable copies of the same benchmark world, Forge 47.4.26 and Java 17. The published files are scoped, sanitized evidence; raw personal pack files, identity/account data, private full logs and desktop screenshots are excluded.

The combined dimension/entity engine completed an NVIDIA OpenGL scene/save/exit without the packet rescheduling loop. Extended play tests initially exposed an MTS old-world follower removal failure; the corrected owner handoff passed all seven OpenGL reload/entity/dimension checks. The OpenGL smoke is a playability gate, not an FPS comparison.

Upstream Mesa Zink 26.2.4 proved hardware OpenGL 4.6 over Vulkan 1.4 on the RTX 4090, then rendered the full pack's scene and saved normally. A diagnostic capture measured roughly 144 FPS, versus roughly 226 FPS in an earlier integrated native OpenGL capture. These were unmatched diagnostics; the translation path has not earned promotion as a performance upgrade. The opt-in WGL command-marshalling experiment failed its hardware probe and was rejected; verified upstream DLLs were restored before the next experiment. The supplied rejected patch must not be enabled or shipped.

Field Guide's latest official release is paired with a narrow query index/split reuse addon, and ModernFix is updated to 5.27.85. ModernFix dynamic model loading was rejected after a missing-model regression. No visual quality setting was lowered. Matched repeat startup and FPS measurements are complete below. Neither 2400 FPS in this pack nor zero hitching has been established.

A separate WGL presentation patch initializes Vulkan loader metadata and forwards later WGL swap-interval changes. Hardware presentation/readback passed at intervals 0, 1 and 0 without GL errors. Actual Minecraft still measured 142.55 FPS (1% low 59.67) in one diagnostic run. JFR was requested but no file was produced before normal game exit; no profile is available. That instrumented run is excluded from promotion comparisons. The apparent game refresh-rate behavior remains unresolved.

The completed [native OpenGL ABBAAB series](../noxviola-matched-20261005/REPORT.md) measured median 219.39→175.22 FPS and median startup 166.40→152.82 seconds. Promotion was rejected for frame-performance regressions. Seven OpenGL feature smoke checks passed; full-pack 2,400 FPS and no hitching remain unproven.

An exact-distance Grassier Grass sort experiment passed algorithm parity tests; native performance validation is pending. A cumulative mod-by-mod study starts from the accepted checksummed vanilla scene. Live Noxviola remains untouched.

## Native grass continuation

The [partial native Vulkan grass data](../cumulative-foundation-20261005/REPORT.md) now includes six sole-addon 1.0.1 captures: median FPS 1,202.96 to 1,611.06 (+33.92%), with improved median lows/p99. Six reload/animation/trail/dimension smoke checks passed and reviewed native close-ups show the original grass. This is not a full Noxviola result, all-style/shader-provider parity or zero-hitch acceptance. Embeddium/Zink remain excluded and Oculus/Iris native interoperability remains required but unfinished. The owner-drain invalidation and pending terrain scheduler experiments have separate validation.
