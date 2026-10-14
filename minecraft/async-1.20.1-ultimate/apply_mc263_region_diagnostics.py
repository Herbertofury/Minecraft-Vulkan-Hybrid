#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mc263_region_diagnostics.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
command = root / "common/src/main/java/com/axalotl/async/common/commands/Mc263Command.java"
if not command.is_file():
    raise SystemExit(f"missing Mc263Command.java: {command}; apply_mc263_diagnostics.py must run first")

text = command.read_text(encoding="utf-8")
old = '''                .append(Component.literal("\\nStructure locate cache: ").withStyle(ChatFormatting.WHITE))\n                .append(enabled(AsyncConfig.enableMc263StructureLocateCache.getValue()))\n                .append(Component.literal("\\nDensity Cache2D fill: ").withStyle(ChatFormatting.WHITE))\n'''
new = '''                .append(Component.literal("\\nStructure locate cache: ").withStyle(ChatFormatting.WHITE))\n                .append(enabled(AsyncConfig.enableMc263StructureLocateCache.getValue()))\n                .append(Component.literal("\\nNon-creating region reads: ").withStyle(ChatFormatting.WHITE))\n                .append(enabled(AsyncConfig.enableMc263NonCreatingRegionReads.getValue()))\n                .append(Component.literal("\\nDensity Cache2D fill: ").withStyle(ChatFormatting.WHITE))\n'''
if new not in text:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"source drift: expected one MC26.3 status insertion marker, found {count}")
    text = text.replace(old, new, 1)
    command.write_text(text, encoding="utf-8")

final = command.read_text(encoding="utf-8")
for token in ("Non-creating region reads", "enableMc263NonCreatingRegionReads"):
    if token not in final:
        raise SystemExit(f"region diagnostics invariant failed: missing {token}")

print("HariMultiThread Minecraft 26.3 non-creating region-read diagnostics applied successfully")
