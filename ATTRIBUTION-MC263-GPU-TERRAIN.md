# HariMultiThread Ultimate 2.3 GPU terrain attribution

Hari's Forge 1.20.1 GPU-indirect terrain lane is an adaptation of ideas and selected algorithms from **Alloyium 1.0.0** by MisoPy / misosoupTgit, source commit `5130a2e1c023847fe469ef9e6f083391234115b5`, licensed LGPL-3.0-or-later.

Adapted concepts include:

- treating Embeddium's static VBO/IBO as read-only authoritative terrain geometry;
- compute-shader frustum compaction into `DrawElementsIndirectCommand` records;
- `glMultiDrawElementsIndirect` / `MultiDrawElementsIndirectCount` submission;
- per-region indirect-command caching and mesh-storage invalidation epochs;
- small-batch CPU indirect generation to avoid compute-dispatch overhead.

Hari deliberately does **not** copy Alloyium's old compute vertex-export experiment. No terrain position, normal, UV, light, material, or texture data is rewritten by Hari.

The integrated Hari code is distributed as part of HariMultiThread Ultimate under GPL-3.0-only. The original Alloyium project remains copyright its contributors and is available under LGPL-3.0-or-later.
