# HariMultiThread Ultimate 2.1.1-noxviola.1

Forge 1.20.1 performance release built on the fully verified 2.1.0-noxviola.1 correctness/runtime stack.

## Release target

- Minecraft 1.20.1
- Forge 47.4.23
- Java 17
- Upstream base: `JustHari01/HariMultiThread@f381611c2d71a85192e2028f9e30c03823a6482b`
- License: GPL-3.0-only
- Mod id retained: `harimt`

## Spatial Vulkan broad-phase optimization

Deferred entity pushes no longer upload every live entity in the entire dimension to the Vulkan collision broad phase. Hari now forms the conservative union of the deferred source AABBs and asks vanilla's `ServerLevel#getEntities` spatial index for only live entities that can intersect that query region.

This is a broad-phase input reduction only. Vanilla/Forge collision semantics remain authoritative: the exact replay query must still match the source AABB, candidate AABBs are rechecked in double precision, normal predicates still run, and cramming/Forge hooks/`doPush` are unchanged. GPU failure or incomplete data still falls back to vanilla.

## Measured real-Forge result

The acceptance run used real Forge 47.4.23 with three fixed entity workloads, seven MSPT samples each, exact tagged-population parity, and a packaged runtime correctness gate.

In the workload with 256 overlapping cows plus 512 distant loaded markers, compared with the verified 2.1.0 path:

- median MSPT improved **12.18%** (`15.637 -> 13.732 ms`);
- mean MSPT improved **15.03%** (`16.024 -> 13.616 ms`);
- Vulkan dispatch improved **69.50%** (`3.159 -> 0.963 ms`).

Normal spread/dense workloads stayed inside strict no-regression bands. Every baseline/candidate sample preserved exactly 256 benchmark cows, and the noisy workload preserved exactly 512 distant markers on both sides.

The CI runner used Mesa llvmpipe CPU Vulkan on an AMD EPYC host. These results prove the real Forge workload improvement and correctness of spatial pruning under the tested runtime; they do not promise the same dispatch percentage on a particular discrete GPU.

## Preserved 2.1.0 correctness stack

2.1.1 retains the prior release's async entity barriers, spawn hardening, C2ME/DimThread interop, palette save locking, isolated LWJGL Vulkan backend, conservative GPU AABBs, bounded fallback behavior, persistent GPU config, circuit breakers, and post-barrier vanilla push replay.

## Release gates

Publication remains last and requires deterministic reconstruction from the pinned source, compatibility/static invariants, Java 17/Forge compilation, packaged shader/backend verification, real Forge dedicated-server Vulkan/fallback/save/restart proof, and a real Forge client + integrated-server rendered-world gate.
