# HariMultiThread Vulkan Hybrid — current work checkpoint

Updated: 2026-10-01. Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Canonical branch: `async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926`.

The repaired 2.4.1 performance candidate is implemented at product commit `f98d69e4ac571d25cdaf727bc22758ddc916bab4`. Its JAR is 30,705,747 bytes, SHA-256 `243808f1352a6f226f2d369112daf8a7dcc2da4e1a077bb3aa18a60b93ad980f`. Compile/repro run 36818650293 passed: all focused regressions, original failure propagation, actual-mixin passenger deadlock negative control, packaging, settings gates and identical clean rebuild.

Native server/C2ME run 36818650277, packaged clients run 36818650271 and Khronos synchronization-validation/C2ME chunk travel at QA commit `4a8ab26d14b2c9aa9e7cb6fd257a1c813377aaee` remain in progress. The candidate is not yet promoted. See PERFORMANCE-RELEASE-CHECKPOINT.json for exact next actions and VULKAN-PERFORMANCE-2.4.1.md for implementation and primary-source research. Complete working source has 984 files and matches a fresh 23-step immutable-source reconstruction byte for byte, including all 44 builder files omitted by the prior archive filter.

The earlier 2.4.1 candidate `418ed90` is rejected because the packaged C2ME client revealed a worker/owner passenger-spawn lock cycle. The real live JVM dump is preserved; the whole passenger tree now hands off before the outer dimension lock. Do not rerun unchanged rejected code or disable C2ME safety checks.

The previous verified 2.4.0 integrity release remains available through INTEGRITY-RELEASE-CHECKPOINT.json and INTEGRITY-DELIVERY-RECEIPT.json. Preserve every shader, ownership, index, failure, config, graphics and packaging correction. Native proof is Linux Mesa software; no Windows/RTX 4090 FPS claim.
