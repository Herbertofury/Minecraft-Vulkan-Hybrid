# Complete Noxviola: compatibility repairs and startup trial

Actual Minecraft1.20.1/Forge47.4.26/Java17 on this computer reaches the original benchmark world with all gameplay mods retained after three targeted compatibility repairs. The seven-step smoke passes resource reload, original Physics/octopus spawn/death, server/client Nether transfer and server/client return. This is bounded playability acceptance; hundreds of pre-existing missing/malformed model warnings remain, arbitrary content and active shaderpack parity are unaccepted. Full-pack1500/2400FPS has not been reached.

The complete352-JAR measurement includes the diagnostic recorder. The RTX4090/driver610.88 renderer is **OpenGL fallback**, selected by the real Hari gate because Oculus and ForgifiedFabricAPI Indigo conflict. The earlier2000+ native Create/grass scene and2432-2470 smaller native baseline are different mod sets/scenes and cannot establish this complete pack's FPS. The fallback is not an equivalent native Vulkan execution path.

## Repeated startup A/B/A/B

Both variants use the identical352 active archives, copied pristine world, camera,12GiB heap and original1920x1080 Fast/RD12/SD11/all-particles/mipmap4/FOV70 settings. Vsync is off; maxFps260 is Minecraft's Unlimited slider endpoint, not a260FPS cap. Pause-on-focus-loss is disabled only for owned automated tests. B changes only ModernFix's optional lazy-model setting. No new executable/provider is installed.

| Run | Average FPS | 1% low FPS | p99 ms | Game-to-world s |
| --- | ---: | ---: | ---: | ---: |
| A1 default | 56.48 | 23.59 | 36.7595 | 173.31 |
| B1 dynamic | 119.34 | 48.86 | 18.5733 | 138.16 |
| A2 default | 97.89 | 41.88 | 21.5188 | 150.04 |
| B2 dynamic | 87.42 | 40.55 | 21.8460 | 136.00 |

Median game-to-world161.674735 ->137.080535s (-15.21%), with menu-to-world timings and launcher/JVM/scene boundaries in [the raw study](startup-study.json). Two repeats per variant, warm/repeated filesystem caches and continued outside workloads limit attribution. Frame ranges overlap; B2 regresses against A2. Lazy models are rejected for retention. A separate diagnostic B smoke passes all seven checks but its FPS is excluded because reload/death/dimension actions and one render-stack dump interrupt the scene. This does not establish cold-start improvement or hitch-free play.

## Recoverable failures and fixes

EnhancedCelestials2Shaders initially refuses startup without Embeddium despite no direct dependency API usage: [a pinned metadata-only optional-renderer recipe](../../../compat/celestials-optional-renderer/README.md) changes that dependency, retains all code/assets and format15. GeckoBetterFPS next fails12 mod constructors through an unavailable SodiumVertexBufferWriter; only that auxiliary optimizer is disabled, original GeckoLib retained. Original Physics then incorrectly treats Iris-only as proof of Sodium, crashing original octopus death; [the pinned provider detection repair](../../../compat/physics-renderer-detection/README.md) preserves all other renderer branches and passes32 extracted-bytecode truth-table cases. Oculus uses the existing exact memory-copy repair. Original source and private local JAR copies remain recoverable; no restricted archive is published.

Loading failures are diagnosed and the exact owned unloaded game closes normally. A clean trial with focus-loss pause enabled never captures because Essential keeps presenting the pause menu; it is rejected, retained and closed normally before the matched trials. No warning/error is hidden. [Failures](failed-trials.json), [default smoke](full-default-seven-check-smoke.json), [dynamic diagnostic smoke](dynamic-seven-check-smoke/QUALIFICATION.json), [visual qualification](VISUAL-REVIEW.json).

The original HybridAquatic bottle variants and BitsNBobs chain-rope-half-magnet model references are missing in default and dynamic logs. Neither is proven a new lazy-model bug, and no substitute geometry is invented. Reviewed capture images show the same original terrain/water/trees/structures/hand/books/minimap, not every asset.

## New optimizer removal baseline

The user's subsequent request removes all20 auxiliary performance mods, including ModernFix and the optional grass helper, from the isolated candidate. Original GrassierGrass and gameplay/library/configuration/world-generation features remain. [New baseline decision and measurements](no-aux-performance/REPORT.md) are separate because the mod set differs. No removed optimizer is automatically re-added from this historical study. The integrated Hari/DimThreads core and required correctness/interop helpers remain.

Every frame, CPU/GPU sample, matched mod/config hash and diagnostic is retained here or in the Desktop private raw archive. Global GPU utilization includes unrelated workloads; they were never stopped. Personal Noxviola, worlds, logged-in account and saved launcher profiles remain unchanged. This evidence does not claim full native compatibility,1500FPS,2400FPS, zero hitches or all-content correctness.
