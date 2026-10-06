# Current complete-pack state: native Vulkan required

The user's latest instruction removes Oculus and Indigo and forbids OpenGL fallback. The isolated candidate now contains328 active archives without the recorder: all20 auxiliary optimizers are recoverably disabled; Oculus plus its required shader-only IrisSearch/EnhancedCelestials shader addon are disabled; only Indigo is removed from ForgifiedFabricAPI's bundle. Original gameplay cores, original Grassier Grass, necessary libraries and correctness helpers remain. No optimizer has been re-added. Personal Noxviola is untouched.

The exact pinned [FFAPI recipe](../../../../compat/ffapi-no-indigo/README.md) retains the other44 bundled APIs byte for byte and all other code/assets/notices, with resource format15. [Provider removal hashes](REMOVAL.json). Original FFAPI and every disabled JAR stay recoverable. The native-only original Create14 backend, already accepted in14 bounded original content checks, is staged; the unmeasured small15 performance prototype is not installed. Arbitrary Create/whole-pack acceptance remains incomplete.

## Native requirement and preflight

Hari23 builds locally with Java17/Forge47.4.26. SHA256 `551d6047f3d7638a964050ca7f00c6e4eeb56a2c432b43f452f9ded68229e3a9`. All750 packaged classes outside the session gate and its two nested records are byte-identical to tested22. The new policy `config/harimt-native-required.properties` contains `requireNative=true`. It blocks a rejected session before OpenGL fallback and disallows forcing past the compatibility gate. Nine production-gate checks pass, including native-positive, renderer conflict, explicit-mode/file requirements, inability to weaken the requirement, forced-bypass rejection and invalid/unreadable policy failures. [Build/install/source parity](INSTALLED.json), [actual policy controls](native-policy-tests.json).

The [read-only installed-gate preflight](../../../../tools/native-required-preflight.py) has cleared the Oculus/Indigo renderer conflicts, but still finds669 untranslated references,360 distinct APIs, in22 remaining archives. These are bytecode references, not proof that every path executes. They require real implementations or pinned reachability audits with feature evidence. No reference is silently whitelisted, stubbed or dropped. [Every exact descriptor, class/module location and gate decision](PREFLIGHT.json).

| Remaining archive | Untranslated references |
| --- | ---: |
| physics-mod-3.0.20-mc-1.20.1-forge-MVH-ProviderDetection-v1.jar | 197 |
| Essential_1-5-0-1_forge_1-20-1.jar | 177 |
| casualswing-0.1.0-1.20.1-Forge.jar | 137 |
| xaeroworldmap-forge-1.20.1-1.46.0.jar | 47 |
| xaerominimap-forge-1.20.1-26.5.0.jar | 42 |
| Arcana-1.1.4-loader-hybrid.jar | 24 |
| lodestone-1.20.1-1.6.4.1.jar | 10 |
| tacz-1.20.1-1.1.8-hotfix.jar | 8 |
| CreateDragonsPlus-1.11.9.jar | 4 |
| moonlight-1.20-2.16.35-forge.jar | 4 |
| malum-1.20.1-1.6.7.jar | 3 |
| ntgl-1.20.1-3.1.2.jar | 3 |
| guideme-20.1.15.jar | 3 |
| Botania-1.20.1-456-FORGE.jar | 2 |
| lootbeams-1.20.1-1.2.6.jar | 1 |
| createbigcannons-5.11.4-mc.1.20.1-forge.jar | 1 |
| Macabre-0.9.2-Eye-UI-QoL-Performance-Fix-REPLACEMENT-2.jar | 1 |
| sweet_calamity-1.0.1.jar | 1 |
| ScorchedGuns-0.5.5-1.20.1-Noxviola-DimThread-Interop-v1.jar | 1 |
| CrashAssistant-forge-1.19-1.20.1-1.11.12.jar | 1 |
| Sounds-2.2.1+1.20.1+forge.jar | 1 |
| supplementaries-1.20-3.1.43-forge.jar | 1 |

Physics and Essential include real GL shader/resource/draw paths; CasualSwing bundles a separate Flywheel1.0.6-beta OpenGL backend despite the repaired original Create backend. Xaero maps, Arcana, Lodestone and smaller gameplay call sites also remain. Removing those gameplay mods or their draw features is not authorized as an FPS shortcut. A complete native port has not been implemented by simply removing Oculus/Indigo.

Because native preflight is blocked, **no new Minecraft game or OpenGL fallback was launched after the latest instruction**. The owned automated controller is disabled; manual mode retains normal pause-on-focus-loss behavior. The runner now preflights native-required profiles before world/config replacement or starting the launcher, retaining the report and preventing a repeated loading/error-screen loop. Do not present this candidate as a ready native full pack.1500/2400FPS, active shader replacement, all-content correctness and zero hitches remain unachieved.

## Preserved optimizer-removal diagnostic

Before the later native-only instruction, one332-JAR full-pack run, including the recorder, completed30.0073s at32.0255FPS, one-percent low19.9220, p9946.2047ms and max60.3136ms. It saved/exited normally. The old runner incorrectly rejected a complete capture because it required more than1000 frames; all961 intervals are now retained. The frame count threshold is corrected without changing the game or FPS. [Raw frames, input hashes, telemetry, screenshots and qualification](historical-slow-fallback/fps.json).

This is one slow OpenGL diagnostic, not a renderer crash or an optimizer-specific A/B result. All20 helpers changed together. Further fallback repeats/smoke were canceled by the latest instruction. It proves neither benefit from removing all optimizers nor harm from any individual one. The prior352-JAR ModernFix lazy-resource A/B/A/B remains [separate historical evidence](../REPORT.md); it cannot establish this different mod set's startup or FPS. There are no new native full-pack FPS results to report.

GPU/CPU telemetry includes continued unrelated workloads; none was stopped. Original saved worlds/auth/saved launcher profiles remain unchanged. New narrow repairs, source/provenance and exact resource metadata are reviewable on the feature branch, not merged/released or promoted into personal Noxviola.

The actual runner failure-path check passes: copied world hashes and options remain exact, no launcher/game process is created and no fallback is started. [Recorded check](runner-block.json). Normal manual focus behavior is restored afterward.
