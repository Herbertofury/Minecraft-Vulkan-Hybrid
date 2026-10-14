# HariMultiThread 2.2.0 MC26.3 Corrected Recovery Checkpoint

Trigger: 2026-09-17

This checkpoint supersedes the earlier recovery candidate. The final MC26.3 structure-locate backport now includes both halves of the 26.3 optimization:

- bounded repeated structure-storage miss caching; and
- non-creating reads/scans for definite missing `.mca` region files, invalidated before writes.

The real Forge runtime gate must prove that `/locate structure minecraft:pillager_outpost` creates zero new region files before this candidate can publish. Cache2D remains experimental/OFF by default after its ABBA result failed the >=1% median-gain promotion rule.

All prior async/Vulkan, provider-precedence, compatibility-audit, save/restart, persistent-mob, repeated-locate, fresh-chunk and rendered-client gates remain mandatory.
