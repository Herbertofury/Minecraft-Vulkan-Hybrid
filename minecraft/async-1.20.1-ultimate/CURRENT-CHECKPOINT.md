# HariMultiThread Vulkan Hybrid — current verified checkpoint

Updated: 2026-09-30. Canonical branch: `async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926`.
Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17.
Product commit: `8ed1e557ae30502fc6a1196e7a843fe2d64cbe9a`.
QA-only commit: `c3ca9efe46901f87ceae57f1ab554b477483f1c1`.
JAR SHA-256: `ce7003e81ebafe14653719bc6fb67f278dfa624b5c4c9245f536b704186fcdb8`; 30693714 bytes.

The complete integrity release is accepted. Compile/repro runs 36769199186 and 36770319923, packaged-client run 36769199184, and native-server run 36770320239 passed. Their JAR bytes match. See INTEGRITY-RELEASE-CHECKPOINT.json and UPSTREAM-INTEGRITY-AUDIT.md for exact inputs and acceptance evidence.

Tick and task failures propagate after active workers converge; the imported tick swallowing and empty emergency-save redirect are removed. Shader JSON/imports retain real semantics and uniform values. Native uniform cleanup is idempotent. Config commands atomically persist complete TOML settings. Fabulous graphics is preserved by selecting OpenGL before Vulkan mixins apply. Earlier index/VBO/image-sync/packaging fixes remain intact.

Runtime proof includes resource reloads, resize, Vulkan/Embeddium/Fabulous paths, saved-world reopen, both intentionally injected tick failure paths, durable GPU toggles, GPU-vs-CPU verification, and all 256 tagged entities retained across restart and failure recovery. All 40 focused cases and 101 atomic-save iterations pass. Linux software-driver proof does not establish hardware FPS or Windows/macOS runtime correctness. Normal offline-auth and Embeddium support notices are classified and retained.

Current exact next action: none. Reuse accepted evidence and source pins; do not restore an older branch or rerun obsolete 2.2/2.3 recovery work. Historical checkpoints remain in Git history.

Verified delivery: [mod JAR](https://drive.google.com/file/d/1zi_YwVnHL00VAF-kN4Wtj9oboxblJdcG/view) and [full source/evidence bundle](https://drive.google.com/file/d/10lob_1zvY28_MdSFj1SBUJXWyWwD6sFE/view). Both were uploaded, downloaded, and byte-verified; every bundle manifest entry passed. Prior releases remain available. See INTEGRITY-DELIVERY-RECEIPT.json.
