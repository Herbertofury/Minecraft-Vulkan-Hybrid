# Oculus vertex-memory startup repair

This optional recipe repairs one unconditional reference to Embeddium's memory-copy API in the user's pinned custom Oculus 1.8.0 JAR. It redirects `copyMemory(long, long, int)` to a small LGPL-3.0-only helper using the same JDK `Unsafe.copyMemory` primitive. It adds no renderer and preserves every other original entry, shader, configuration and asset. The original JAR is never changed. Minecraft 1.20.1 resource metadata remains format 15.

The original custom Oculus SHA-256 is `2e7e4e713effee15c2dfd2f32e9fe30f71dbc88a53ad70bdf994f6853f818e30`. The locally verified repaired artifact SHA-256 is `39ce418fd9da79a82bf82ef6e58ad5a7965954fbc2b39605f15051c8fbcb4b51`. Generated artifacts contain upstream content and remain private; this repository contains only the patch recipe and helper source. Signed, unknown, duplicate-entry and already-patched inputs are rejected.

With Java 17 and ASM 9.9.1 on the classpath:

```text
python recipe.py ORIGINAL_OCULUS.jar NEW_PRIVATE_OUTPUT.jar --asm-classpath PATH_TO_ASM.jar
python tests/test_memory_copy.py --reference-jar ORIGINAL_EMBEDDIUM_0.3.31.jar
```

The parity test extracts only the hash-verified original memory API into a temporary classpath. It never installs or loads the Embeddium mod into Minecraft. It compares 168 size/alignment combinations, guard bytes and negative-length failure behavior. The actual isolated game then reached the world, captured 513.61 FPS on NVIDIA OpenGL and saved/exited normally. Native shader rendering and extended shader parity were not accepted.

The user subsequently requested the VulkanMod-supported shader option and excluded Oculus from current pack progression. The user then restored Oculus/Iris interoperability to the goal. This repair is retained for that compatibility work; it remains disabled during the active native grass trial. See [the shader compatibility record](../vulkan-shaders/README.md). No performance gain or native shader capability is claimed.

Original projects: [Oculus](https://github.com/Asek3/Oculus), [Embeddium](https://github.com/FiniteReality/embeddium). Oculus and the referenced Embeddium API use LGPL-3.0-only. The custom source artifact and its supplied attribution are preserved privately; no authorship of upstream shaders or rendering code is claimed.
