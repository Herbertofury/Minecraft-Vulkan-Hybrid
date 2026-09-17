#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mc263_release_identity.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
props = root / "gradle.properties"
if not props.is_file():
    raise SystemExit(f"not a HariMultiThread source root: {root}")

text = props.read_text(encoding="utf-8")
old_version = "version=2.1.1-noxviola.1"
new_version = "version=2.2.0-noxviola.1"
if new_version not in text:
    count = text.count(old_version)
    if count != 1:
        raise SystemExit(f"source drift: expected one {old_version!r}, found {count}")
    text = text.replace(old_version, new_version, 1)

old_description = "description=Release-hardened asynchronous entity ticking with real optional Vulkan collision broad-phase; based on HariMultiThread and Async"
new_description = "description=Release-hardened asynchronous entity ticking plus result-preserving Minecraft 26.3 performance backports and optional Vulkan collision broad-phase; based on HariMultiThread and Async"
if new_description not in text:
    count = text.count(old_description)
    if count != 1:
        raise SystemExit(f"source drift: expected one release description, found {count}")
    text = text.replace(old_description, new_description, 1)

props.write_text(text, encoding="utf-8")

final = props.read_text(encoding="utf-8")
for token in (new_version, new_description):
    if token not in final:
        raise SystemExit(f"release identity invariant failed: missing {token}")

print("HariMultiThread Ultimate 2.2.0-noxviola.1 release identity applied successfully")
