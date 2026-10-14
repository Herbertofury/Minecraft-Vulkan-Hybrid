# HariMultiThread Ultimate 2.3 — Persistent GPU Scene Native Acceptance

Accepted: 2026-09-18

## Canonical identity

- Repository: `Herbertofury/ProjectDump`
- Branch: `async-1.20.1-ultimate-2.3.0-persistent-scene-20260918`
- Native trigger/source head: `ef7e92d8f607408a8bd44e5e48decdcf0fad3245`
- Canonical Hari upstream: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
- Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17
- Embeddium QA source: `FiniteReality/embeddium@87d1a75aecc230a25a1c152df8fbd7d2ca8bd2fd` (0.3.31)

## Accepted build

Compile workflow run: `35332910135`
Compile job: `105561064508`
Compile artifact: `10541337925`
Compile artifact digest: `sha256:a2e39fbe4874de2a0fae314eca094fcf2ca6c3ef04b3e115c1a5f4e5fd0498d2`

Accepted JAR:
`HariMultiThread-Ultimate-1.20.1-2.3.0-noxviola.1-persistent-scene.jar`

JAR SHA-256:
`0d80199e70b07baed8a73bab440bd3a6ae9945259a33db7f43b95cef3515c656`

Compile/package proof:
- deterministic 2.2 -> 2.3 -> persistent reconstruction passed;
- persistent Mixin namespace post-layer passed;
- GLSL validation passed;
- production collision SPIR-V generated and packaged with magic `03022307`;
- Forge build passed;
- JAR contains `com/axalotl/async/forge/client/hari263/render/SectionStorageGenerationAccess.class`;
- JAR does not contain or reference the forbidden Mixin-package helper path;
- JAR integrity passed.

## Native acceptance

Native workflow run: `35333268384`
Native job: `105562222622`
Native artifact: `10541659681`
Native artifact digest:
`sha256:8fd3cad3921fbb237a0b10ba404942a6cd392e7e744a56a9ecd1305eccc1f4d7`

Observed required client markers:
- `Hari 26.3 render bridge: provider=HARI_GPU_INDIRECT`
- `Hari GPU terrain initialized: GPU threshold=1 regionCache=true persistentScene=true`
- `Hari persistent GPU scene active: meshlets=2 storageGeneration=2 indirectCount=true`

Observed negative gates:
- no `Hari GPU terrain failed`;
- no `IllegalClassLoadError`;
- no `MixinApplyError`, `InvalidMixinException`, or `InjectionError`.

Rendered-world proof:
- attempts 1-3: loading frame, 9 colors, gray stddev 0.033304;
- attempt 4: accepted rendered frame, 1278x695, 6390 colors, gray stddev 0.177359, 452837 bytes.

Server regression proof:
- exact packaged JAR passed the full two-phase Forge server gate;
- Vulkan broad-phase verification/fallback/save/restart/re-enable workflow passed;
- no client-only class leakage on dedicated server.

## Drive persistence

Native evidence bundle:
- Drive file ID: `1Qq8HDFu28g8r_p3ZkpE_AXBg6_yHpeM7`
- size: `7748990` bytes
- SHA-256: `8fd3cad3921fbb237a0b10ba404942a6cd392e7e744a56a9ecd1305eccc1f4d7`
- Drive metadata size matched the local/GitHub artifact size exactly.

Compile run-5 bundle:
- Drive file ID: `1KOqWmkqwxOtJLZOvJsGKXaIzMdZWz79y`

## Accepted behavior

This certifies the portable persistent GPU scene metadata lane in the CI native Forge environment. It does not claim RTX 4090 performance because CI used Mesa/llvmpipe and the local Project Constellation PC bridge is not armed in this chat.

Embeddium remains authoritative for chunk meshes, UVs, lighting, materials, visible-section lists, and block-face-culling semantics. Sorted/translucent terrain remains Embeddium-owned. Provider precedence and fail-closed fallback remain intact.

## Exact next action

Branch from this accepted state into the RTX/global-scene lane. First milestone is mirror-only geometry ownership: hook the exact accepted Embeddium 0.3.31 `RenderRegionManager.uploadMeshes(CommandList, Collection<ChunkBuildOutput>)` TAIL, mirror SOLID/CUTOUT final NativeBuffer bytes and seven face ranges into the Hari arena, remove entries on null mesh/section unload, but do not take render ownership yet. Compile, lifecycle-test and native-test that mirror before enabling NV addressable/task/mesh submission.
