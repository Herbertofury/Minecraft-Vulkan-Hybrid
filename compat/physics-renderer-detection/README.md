# Physics Mod optional renderer detection

The pinned original Physics Mod3.0.20 treats the presence of Iris/Oculus as proof of a Sodium provider. With Oculus installed but Embeddium/Sodium excluded, actual GeckoLib octopus death rendering crashed on the unavailable `VertexBufferWriter`. This local recipe changes exactly one Iris-implies-Sodium boolean branch in `StarterClient`; genuine Sodium/Rubidium/Embeddium decisions remain. Original Physics Mod standard consumers, debris, assets, native libraries, settings and shader integration remain. No consumer is replaced by a stub and no missing renderer API is fabricated.

The recipe inspects bytes and extracts the actual patched provider decision into an isolated regression fixture. It never executes the original mod initializer.32 provider/optional-Iris combinations check the truth table, including the failing no-Sodium Oculus case. Upstream is All Rights Reserved; original/generated archives remain private. New patch/recipe code is GPL-3.0-only. Runtime feature/complete-pack FPS results are separate; this is not native Vulkan shader acceptance.

The original archive advertises pack format7. The private output corrects this to Minecraft1.20.1 format15; this is the sole additional changed entry. All other code/assets/licenses remain exact.
