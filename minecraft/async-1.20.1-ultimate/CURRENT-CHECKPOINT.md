# HariMultiThread Vulkan hybrid — current checkpoint

Updated 2026-10-01. Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Canonical branch: async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926.

Recommendations 1, 2 and 5 are implemented. Product ba05e9589960f3ab255926c50b007f9d7ce1b4ea produces HariMultiThread-Ultimate-1.20.1-2.4.1-vulkan-hybrid.jar, 30,710,062 bytes, SHA-256 bc91ce5c87610eaeb0a0c87f9b80d44633450adf487d17a0e0e71fb79b8ad446. Complete merged source contains 986 files and matches a fresh 23-step immutable-source reconstruction byte for byte, including all 44 builder-package sources.

Compile/reproducibility run 36866930953, native server/C2ME recovery run 36866930874 and seven packaged production-client configurations in run 36866930792 passed. All produced the exact same JAR. Thirteen focused Java regression suites passed. Strict checked world RNG, original tick failures and complete failure barriers remain enabled.

Extra Khronos/C2ME terrain travel is running in 36887875665, QA commit 62322d04cb5e9b9b91cb368db57b62a9e2220757, on the same immutable JAR. The acknowledged travel, validation layer and two-worker server run 36870710724 passed its automated checks, but manual review found a premature far screenshot containing only 16.8% terrain. Final QA requires at least 60% non-sky scene coverage for three consecutive captures and retains every progression screenshot. The real command acknowledgements, strict RNG, validation/error checks and native two-worker server gates remain required. Do not promote until this stronger gate passes.

The source-only checkpoint is already saved and fully read back from Drive: file 1oSe-i76YbcqH2CmBXrJoTTwMLBC8qqxS, SHA-256 0d64b3d150dd5c86e72d07e9b46e2124ccc8d7de0d985c4413336bd2f68e3a99. Historical repaired failures and complete diagnostics are retained, never counted as current acceptance. See PERFORMANCE-RELEASE-CHECKPOINT.json for exact next actions.

Native proof uses Linux Mesa software drivers. It does not measure Windows/RTX 4090 FPS or guarantee arbitrary third-party modpacks.
