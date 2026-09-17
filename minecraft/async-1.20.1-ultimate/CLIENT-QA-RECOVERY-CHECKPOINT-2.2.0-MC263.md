# HariMultiThread 2.2.0 MC26.3 Client QA Recovery

Trigger: 2026-09-17

The previous corrected candidate passed production reconstruction, compatibility audit, SPIR-V, Forge build/JAR packaging, packaged Vulkan/fallback/save/restart QA, and the full MC26.3 server/region-read gate including zero new `.mca` files from `/locate structure minecraft:pillager_outpost`.

Its only failure was the rendered-client harness invoking `./gradlew` after the recovery lane had intentionally bypassed that wrapper route and installed Gradle 8.11.1 directly. `runtime_client_qa.py` now prefers the pinned `gradle` executable when available and retains `./gradlew` as the portable fallback.

No production mod behavior was changed for this recovery. All prior release gates remain mandatory.
