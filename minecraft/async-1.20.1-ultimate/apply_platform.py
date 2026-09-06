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
print("Forge target updated to 1.20.1-47.4.23")
