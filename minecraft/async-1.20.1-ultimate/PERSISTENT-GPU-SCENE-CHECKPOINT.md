# HariMultiThread Ultimate 2.3 — Persistent GPU Scene Checkpoint

Base: `651a7c2337ba7e43b66dd7bc70da0cf146072e57` on `async-1.20.1-ultimate-2.3.0-gpu-terrain-20260917`.

This child layer removes two avoidable CPU-side terrain costs while preserving Embeddium's authoritative mesh/visibility behavior:

- draw-boundary metadata is uploaded to a per-storage persistent GPU buffer only when Embeddium mutates that region/pass storage or replaces its VBO;
- CPU-built command caches use a pass-local storage generation plus exact visible-section/visible-face signature instead of the previous global epoch + camera-block invalidation.

Parity constraints:

- Embeddium still owns chunk meshing, VBO contents, UVs, light, material data and visible-section lists;
- Hari reads Embeddium's `useBlockFaceCulling` option every frame and preserves its face-selection rule;
- sections absent from Embeddium's current render list receive a zero GPU visibility mask and cannot be drawn;
- sorted/translucent passes remain owned by Embeddium;
- provider precedence and fail-closed fallback from 2.3 remain unchanged.

Next gate: reconstruct certified 2.3, apply this layer, validate GLSL, compile Forge 47.4.23 with Embeddium 0.3.31 API, then run the native rendered-client gate before any performance claim.

Native acceptance instrumentation: `-Dharimt.gpuTerrainCpuThreshold=1` may be used only by QA to force the persistent dispatch path. Runtime acceptance requires `Hari persistent GPU scene active:` plus a rendered-world screenshot and no fail-closed marker. Normal installs continue to use the configured/default threshold.
