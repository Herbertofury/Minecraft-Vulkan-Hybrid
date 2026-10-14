#!/usr/bin/env python3
"""Move the persistent-scene generation helper out of the Mixin-owned namespace."""
from __future__ import annotations

from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_persistent_mixin_namespace_fix.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
old = root / "forge/src/main/java/com/axalotl/async/forge/mixin/client/embeddium/SectionStorageGenerationAccess.java"
new = root / "forge/src/main/java/com/axalotl/async/forge/client/hari263/render/SectionStorageGenerationAccess.java"
mixin = root / "forge/src/main/java/com/axalotl/async/forge/mixin/client/embeddium/MixinSectionRenderDataStorage.java"
renderer = root / "forge/src/main/java/com/axalotl/async/forge/client/hari263/render/HariTerrainRenderer.java"
scene = root / "forge/src/main/java/com/axalotl/async/forge/client/hari263/render/PersistentRegionSceneCache.java"

old_pkg = "package com.axalotl.async.forge.mixin.client.embeddium;"
new_pkg = "package com.axalotl.async.forge.client.hari263.render;"
old_import = "import com.axalotl.async.forge.mixin.client.embeddium.SectionStorageGenerationAccess;\n"
new_import = "import com.axalotl.async.forge.client.hari263.render.SectionStorageGenerationAccess;\n"
bridge_import = "import com.axalotl.async.forge.client.hari263.render.HariWorldBridge;\n"

# Idempotent recovery: either transform the certified child layout or validate
# that this exact namespace correction has already been applied.
if old.is_file():
    text = old.read_text(encoding="utf-8")
    if old_pkg not in text or "public interface SectionStorageGenerationAccess" not in text:
        raise SystemExit("source drift: unexpected SectionStorageGenerationAccess mixin helper")
    new.parent.mkdir(parents=True, exist_ok=True)
    new.write_text(text.replace(old_pkg, new_pkg, 1), encoding="utf-8")
    old.unlink()
elif not new.is_file():
    raise SystemExit("source drift: generation helper exists in neither expected namespace")

for path in (renderer, scene):
    text = path.read_text(encoding="utf-8")
    if old_import in text:
        text = text.replace(old_import, "", 1)
        path.write_text(text, encoding="utf-8")

text = mixin.read_text(encoding="utf-8")
if new_import not in text:
    if bridge_import not in text:
        raise SystemExit("source drift: MixinSectionRenderDataStorage import anchor missing")
    text = text.replace(bridge_import, bridge_import + new_import, 1)
    mixin.write_text(text, encoding="utf-8")

if old.exists():
    raise SystemExit("namespace fix failed: helper still resides in Mixin package")
if not new.is_file() or new_pkg not in new.read_text(encoding="utf-8"):
    raise SystemExit("namespace fix failed: renderer helper missing")
if old_import in renderer.read_text(encoding="utf-8") or old_import in scene.read_text(encoding="utf-8"):
    raise SystemExit("namespace fix failed: stale Mixin-package helper import remains")
if new_import not in mixin.read_text(encoding="utf-8"):
    raise SystemExit("namespace fix failed: mixin does not import runtime helper")

print("Hari persistent-scene Mixin namespace fix applied successfully")
