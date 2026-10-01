# HariMultiThread Vulkan Hybrid — current work checkpoint

Updated: 2026-10-01. Target: Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Canonical branch: `async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926`.

The 2.4.1 performance candidate is implemented at product commit `418ed90c2748990c674a09e5ee1240457567ffb1`. Its JAR is 30,705,542 bytes, SHA-256 `a309c2823ee6a64dae6728bef73b2934e0181abe7f26edc3c18f220147ef08df`. Compile/repro run 36814574523 and native-server/C2ME run 36814574538 passed with identical JARs. All 256 entities and block-entity inventory survived unload/reload/restart and intentional async failures remained visible with a saved-world recovery.

Packaged client run 36815141375 and extra Khronos synchronization-validation/C2ME travel run 36815286781 are still running. The candidate is not yet promoted. See PERFORMANCE-RELEASE-CHECKPOINT.json for exact pending actions and VULKAN-PERFORMANCE-2.4.1.md for changes, primary-source research and repaired runtime discoveries. Current working source preserves all 931 previously archived files and restores 44 omitted builder sources; 984 complete source files are expected.

The previous verified 2.4.0 integrity release remains available through INTEGRITY-RELEASE-CHECKPOINT.json and INTEGRITY-DELIVERY-RECEIPT.json. Preserve that evidence and every shader, ownership, index, failure, config, graphics and packaging correction. Do not restart obsolete 2.2/2.3 recovery. Native proof is Linux Mesa software; no hardware FPS claim.
