# Native Vulkan shader compatibility

Checked 2026-10-05 against the [VulkanMod README](https://github.com/xCollateral/VulkanMod) and [Beryl's official listing](https://modrinth.com/mod/beryl), including its complete public version metadata. VulkanMod recommends Beryl for shaders. Beryl provides an integrated shader pack and requires VulkanMod.

All 16 published Beryl versions are Fabric builds for Minecraft 1.21/1.21.1, 1.21.9-1.21.11 or 26.1.x. There is no Forge 1.20.1 artifact. Beryl's published license is All Rights Reserved and its project metadata supplies no public source URL. No Beryl artifact has been installed into Noxviola, relabeled as Forge-compatible, redistributed or modified. An authorized compatible upstream build, or a separate original shader implementation, is required for this pack.

The user initially excluded Oculus from native progression and requested the supported Vulkan shader option, then restored Oculus/Iris interoperability and comparable performance to the goal. Those APIs remain disabled only in the isolated native trial while Grassier Grass is repaired and tested; their compatibility is pending. The original personal pack and its assets remain preserved. This does not establish a functioning shader replacement, shader-pack equivalence or permanent feature removal.

Hari's current generic OpenGL shader/draw adapters do not implement arbitrary shader compilation, linking and Vulkan draw submission. Removing its renderer safety gate would not supply those missing operations. No gate is forced, unsupported draw is presented as success, or shader feature is claimed working from a menu launch. Native shader testing must capture actual frames, correct effects, errors and matched performance after the implementation exists.

[Iris's official compatibility page](https://www.irisshaders.dev/) explicitly lists Vulkan renderers as incompatible with its released builds. Text inspection found LGPL-3.0 experimental [Iris-Vulkan source](https://github.com/trptrk/Iris-Vulkan/tree/9bae8e9a3c8b6ec34f871657b36331ebcd9f1a84), including a native terrain pipeline, SPIR-V compiler and uniform layout. That pinned source targets Minecraft 1.21.1/Fabric, Java 21 and LWJGL 3.3.6, while this pack uses Forge 1.20.1/Java 17/LWJGL 3.3.1. It is a reference candidate for a real port, not an installed compatible build. The source metadata and nine inspected core-file hashes are preserved; no third-party executable or build script is run.

## Reference source cache repair

The [pinned LGPL reference patch](reference-patches/iris-spirv-full-input-key.patch) fixes `IrisSPIRVCompiler`'s shared integer hash key. Both compiler entry points now distinguish the full source, shader stage, shader name and raw/preprocessed form. The exact supplied record compiles on Java 17 and tests reject both the original hash-only key and a preprocessing-blind variant. Origin hashes, upstream license and patch are retained.

This repair is source-only and is not installed. No third-party build script or executable is run, and it establishes no Forge compatibility, Vulkan shader draw, shader-pack visual result or FPS gain. A functioning port still needs real native framebuffer, compute, shader-resource and draw/lifecycle interoperability.
