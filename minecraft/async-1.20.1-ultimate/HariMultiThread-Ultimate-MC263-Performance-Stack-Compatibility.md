# HariMultiThread Ultimate - Noxviola Performance Stack Compatibility

Overall: **PASS**

This gate runs against the fully transformed HariMultiThread Ultimate source used for the release build.
It verifies architectural separation and the explicit threading/provider contracts that matter for the active Noxviola performance stack.

| Stack component | Result | Reason |
|---|---|---|
| Entity Culling 1.10.5 Noxviola patch | PASS | HariMT has no Minecraft client renderer, frustum, occlusion-culling or OpenGL hooks. Entity Culling remains the sole owner of its client render-culling path. |
| ImmediatelyFast Noxviola patch | PASS | HariMT does not touch RenderSystem/LevelRenderer/GameRenderer or OpenGL batching state. |
| GPUTape Noxviola patch | PASS | HariMT does not own OpenGL framebuffer cleanup/state; its optional accelerator is isolated Vulkan compute. |
| BadOptimizations Noxviola patch | PASS | No client renderer-cache or EntityType renderer path is modified by HariMT. |
| Embeddium / Rubidium / Sodium | PASS (provider precedence) | These renderers own terrain submission. Hari delegates the Minecraft 26.3 MultiDrawIndirect terrain lane and installs no competing LevelRenderer/OpenGL hook. |
| Oculus / Iris | PASS (provider precedence) | Shader providers own transparency/shader compilation. Hari delegates the 26.3 OIT/ShaderC lanes and does not replace their pipeline. |
| Noisium | PASS (additive) | Noisium keeps its generator/section fast paths; Hari's narrow result-preserving Cache2D fill lane does not replace the chunk scheduler. |
| C2ME Forge / HariChunk + DimThread SAFE-INTEROP | PASS | The 26.3 Cache2D mixin is disabled when HariChunk/C2ME owns chunk generation, and barrier pumping is restricted to the current ServerLevel. |
| Physics Mod / Uranus | PASS (conservative) | Third-party entity namespaces stay synchronous unless they explicitly implement HariMT AsyncCompatible; HariMT has no client GL/render ownership. |
| Curios / ApothicCurios | PASS (conservative) | ServerPlayer is always synchronous and third-party entity types default to synchronous ticking, avoiding async capability mutation on the common high-risk paths. |
| Potatoptimize Forge parity-safe defaults | PASS | HariMT does not alter Potatoptimize's enabled safe-default targets; HariMT client/render overlap is absent and its C2ME/DimThread path is separately guarded. |

## Enforced invariants

- Non-Minecraft entity types are synchronous unless they explicitly opt into `AsyncCompatible`.
- `ServerPlayer` and `EnderDragon` remain synchronous.
- C2ME/DimThread wait pumping is dimension-local.
- HariMT common/Forge source contains no client renderer, GUI, Blaze3D, OpenGL, Entity Culling, ImmediatelyFast, GPUTape or BadOptimizations hooks.
- Minecraft 26.3 terrain MDI is delegated to Embeddium/Rubidium/Sodium when present.
- Minecraft 26.3 OIT/ShaderC is delegated to Oculus/Iris when present.
- The result-preserving 26.3 Cache2D fill mixin yields to HariChunk/C2ME ownership.
- Vulkan is packaged as a private isolated backend rather than exposed as a Forge JarJar LWJGL module.

Audit run: `35275559688`
Audit artifact: `10520661322`
Audit artifact digest: `sha256:8e04912d9a068113337d2af7dda5166300263587060ebbbf1cf2fcd65447038b`
