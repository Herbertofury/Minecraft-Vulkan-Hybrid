# HariMultiThread 2.2.0 MC26.3 Recovery Checkpoint

Recovery trigger: 2026-09-17

Reason: the canonical wrapper-based release runners remained in the Forge Gradle build step substantially longer than the already-green 69-second baseline compile. This recovery lane preserves the same pinned upstream, transformations, runtime acceptance, and release assets while installing Gradle 8.11.1 directly through the official Gradle GitHub Action.

Production policy remains unchanged: persistent-mob idle and repeated structure-storage miss caching are enabled; the Cache2D experiment is included but OFF by default after its real Forge ABBA did not meet the default-on threshold.
