# Actual custom-program and fence dependency

Physics Mod contributes197 and Essential177 of the current532 untranslated static references. These counts identify implementation work; they are not runtime cost measurements. The full candidate remains blocked by the unchanged native-required gate.

The pinned Hari23 production adapters also have an important limitation: `GlProgram` stores program/shader metadata but does not compile arbitrary GLSL into a Vulkan pipeline. `GlVertexArray` records object names rather than arbitrary vertex layouts. `GlSync` creates handles without submitting a GPU fence. Mapping the remaining inherited LWJGL/ARB owners to these classes would therefore not make these mods native or preserve their rendering.

`probe.py` executes only the verified Hari23 adapters and its verified installed fastutil dependency, without Minecraft, a GL context or third-party mod initialization. It observes success before compilation, success for invalid GLSL, an empty program linking successfully, and a new unsubmitted fence reporting signaled. These observations explicitly **reject native custom-program/fence acceptance**. They do not describe the separate real Vulkan vanilla pipelines or bounded native Flywheel engine.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
python probe.py <exact-Hari23.jar> <installed-fastutil-8.5.9.jar> <fresh-report.json>
```

The next shared implementation needs actual GLSL/SPIR-V compilation/link validation and reflection; matching uniforms, sampler resources and vertex/index layouts; Vulkan draw submission and framebuffer attachment state; and fences tied to submitted command buffers with correct retirement. Essential's asynchronous screenshot upload must use native transfer ownership rather than create a GL context. Physics needs its actual instanced draw, shader and deferred/MRT paths preserved. Each path requires original-content GPU pixel/lifecycle checks before expanding the accepted translation contract.

[Pinned production observations](../../benchmarks/results/casualswing-native-20261006/runtime-native106/NATIVE-PROGRAM-DEPENDENCY.json) and [specific licensed downport references and obstacles](../vulkan-shaders/DOWNPORT-STATUS-20261006.md) record the inputs. No gate, contract or production adapter is changed by this diagnostic. New diagnostic source is GPL-3.0-only under the project license; proprietary mod code/assets are not included.
