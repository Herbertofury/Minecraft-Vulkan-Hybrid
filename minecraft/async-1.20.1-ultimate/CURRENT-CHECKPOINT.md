# HariMultiThread Vulkan hybrid — current checkpoint

2026-10-01. Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Recommendations 1, 2 and 5 are implemented. Complete source: 986 files, including all 44 builder-package sources.

The owner-chunk product ba05e958 passed reproducible compile, native C2ME server/recovery, seven installed Forge clients and strict Khronos/acknowledged sustained terrain travel with a two-worker server (36887875665). It is superseded after manual review found inherited renderer culling could still drop reachable visible section paths. The actual whole-graph Java negative control renders 11/32 reachable sections with the old heuristic. New reusable deferred scheduling preserves every frustum/occlusion-valid section, with 44,744 checks across all four config modes, queue growth/reset, exact rebuilds/block entities and original frustum/visibility exclusions.

The new product retains all queue/upload, C2ME owner chunk/RNG/lifecycle/sensor and original failure repairs. All runtime gates must rerun on its new exact JAR before delivery. No tick failures or C2ME checks are suppressed. Source-only prior checkpoint remains fully verified in Drive file 1oSe-i76YbcqH2CmBXrJoTTwMLBC8qqxS; preserve all historical artifacts and native screenshots. See PERFORMANCE-RELEASE-CHECKPOINT.json for exact next actions. Linux Mesa proof does not measure Windows/RTX4090 FPS.
