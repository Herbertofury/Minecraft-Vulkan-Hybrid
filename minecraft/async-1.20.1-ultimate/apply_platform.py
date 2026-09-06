#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_platform.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
path = root / "gradle.properties"
if not path.is_file():
    raise SystemExit(f"missing gradle.properties: {path}")

text = path.read_text(encoding="utf-8")
old = "forge_version=47.4.16"
new = "forge_version=47.4.23"
count = text.count(old)
if count != 1:
    raise SystemExit(f"source drift: expected one {old!r}, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")

# The upstream LevelMixin shadows Level.thread by its Mojmap name. Mixin's
# annotation processor does not emit this private field into the generated
# Forge refmap even though it remaps the getBlockEntity redirect. Production
# Forge therefore sees only the SRG field name (f_46423_) and rejects the
# shadow before server bootstrap. A private @Shadow may safely declare aliases;
# keep the normal development name while giving production Mixin the exact
# 1.20.1 SRG fallback.
level_mixin = root / "common/src/main/java/com/axalotl/async/common/mixin/world/LevelMixin.java"
if not level_mixin.is_file():
    raise SystemExit(f"missing LevelMixin: {level_mixin}")
level_text = level_mixin.read_text(encoding="utf-8")
old_shadow = """    @Shadow
    @Final
    private Thread thread;"""
new_shadow = """    @Shadow(aliases = {\"f_46423_\"})
    @Final
    private Thread thread;"""
shadow_count = level_text.count(old_shadow)
if shadow_count != 1:
    raise SystemExit(f"source drift: expected one Level.thread shadow, found {shadow_count}")
level_mixin.write_text(level_text.replace(old_shadow, new_shadow, 1), encoding="utf-8")

print("Forge target updated to 1.20.1-47.4.23 with production Level.thread shadow alias")
