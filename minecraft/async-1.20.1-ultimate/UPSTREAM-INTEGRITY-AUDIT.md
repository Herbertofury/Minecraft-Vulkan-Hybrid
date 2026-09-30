# Upstream integrity audit — 2026-09-30

Scope: Minecraft 1.20.1, Forge 47.4.23, Java 17. Original Hari source is immutable commit f381611c2d71a85192e2028f9e30c03823a6482b. The Forge Vulkan import is pinned to 0ceac5d47f84c910d2f5f6d007b0ffe266c6f736. Original Vulkan current dev head 087ef24e58e5522dec5ccb44f279768786bfb6da was checked; newer Minecraft branches cannot replace the 1.20.1 APIs wholesale.

## Defects repaired in the candidate

- The imported MinecraftMixin caught and swallowed allowlisted RuntimeExceptions around an entire client tick. Removed that redirect and the unused exception boundary and prefix list. Tick failures follow vanilla's reporting path.
- Its empty emergencySave redirect disabled vanilla integrated-world emergency saves. Removed the redirect.
- The production Vulkan log contained a real minecraft:null.vsh failure for forge:rendertype_entity_unlit_translucent. Resolve vertex/fragment programs from the active shader JSON, retain Forge namespace defaults, preprocess imports through the active ResourceProvider, and close resource streams.
- Remove approximate vertex-format-based shader substitution and fabricated zero uniforms. Missing resources, conversion failures, or missing bindings now identify the actual shader defect instead of silently changing visuals.
- Fix the QA client's invalid simulationDistance=4 to the supported minimum 5.

- Hari's entity tick, parallel item, and completed-future handlers also swallowed simulation exceptions. Keep active workers behind the barrier, retain failure causes and secondary exceptions, and propagate the failure to Minecraft. Real Java futures/threads cover eight failure/barrier cases, including cancellation, fatal Error, and restored interruption.

- Preserve a saved Fabulous graphics choice by selecting OpenGL before applying Vulkan mixins, instead of the fork's runtime clamp to Fancy. Read the actual Forge game directory for options and mod discovery; check this choice before compatibility-cache reuse.

- ShaderInstance's replacement close skipped native Minecraft uniform buffers. Close them with the pipeline, including cleanup failure paths, and guard repeated closes. Four actual method-body resource tests fail against the original close implementation and pass against the candidate.
- The restart gate exposed intermittent loss of the saved GPU toggle. Forge autosaves each ConfigValue.set while its watcher can reload between fields. Replace those partial writes with one independent TOML snapshot and atomic filesystem replacement, retain unknown settings/comments, reload the bound config, and invalidate Forge caches. 101 real-library atomic saves passed concurrent read checks; the native command/restart gate also checks the saved false value directly.

## Verification status

All release gates passed. The product commit is 8ed1e557ae30502fc6a1196e7a843fe2d64cbe9a; QA-only commit c3ca9efe46901f87ceae57f1ab554b477483f1c1 changes no product source. Normal and cached rebuilds, independent native-server builds, and packaged clients have identical JAR SHA-256 ce7003e81ebafe14653719bc6fb67f278dfa624b5c4c9245f536b704186fcdb8.

Accepted runs: compile/repro 36769199186 and 36770319923; packaged clients 36769199184; native server 36770320239. Real packaged forgeclient covers Vulkan, Embeddium, preserved Fabulous/OpenGL, resource reloads, resize, an injected external-looking client tick fault, and a new-JVM saved-world reopen. Dedicated-server QA covers GPU-vs-CPU collision verification, durable GPU toggles/restart, a real async entity tick fault reaching fatal Minecraft world reporting/save, and all 256 tagged entities retained after reopening. The intentional server failure arrives as Minecraft's wrapped `Exception ticking world` report.

Forty actual Java resource/barrier/gate cases and 101 atomic settings saves with concurrent reads pass. The original shader close, VBO, and generated-index negative controls fail. Immutable Hari inventory reconciles 108 identical files, 28 changed files, and three reflection implementations replaced by the compiled backend. The pinned Forge Vulkan import reconciles 577 identical files, 13 changed files, and five intentional merged/renamed/removed files. All 165 original 1.20.1 core shader resources have counterparts in the Forge layout, and all 13 original Vulkan access-transformer rules remain in the canonical transformer. These inventories are not claims of byte-identical Fabric/Forge behavior.

Native runtime proof uses Linux Mesa software drivers. No universal modpack, hardware-FPS, or Windows/macOS runtime claim is made.

Offline PortableMC's authentication-service 401 and Embeddium's retained GPU-bridge support notice are external fixture/support diagnostics. Production authentication is not altered and third-party notices are not hidden.
