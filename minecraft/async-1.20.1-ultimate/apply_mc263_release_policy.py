#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mc263_release_policy.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
common_config = root / "common/src/main/java/com/axalotl/async/common/config/AsyncConfig.java"
forge_config = root / "forge/src/main/java/com/axalotl/async/forge/config/AsyncConfigForge.java"
if not common_config.is_file() or not forge_config.is_file():
    raise SystemExit(f"not a transformed HariMultiThread source root: {root}")

common = common_config.read_text(encoding="utf-8")
old_default = '    public static Map.Entry<String, Boolean> enableMc263DensityCacheFill = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263DensityCacheFill", true);\n'
new_default = '    public static Map.Entry<String, Boolean> enableMc263DensityCacheFill = new AbstractMap.SimpleEntry<String, Boolean>("enableMc263DensityCacheFill", false);\n'
if new_default not in common:
    count = common.count(old_default)
    if count != 1:
        raise SystemExit(f"source drift: expected one Cache2D default-on line, found {count}")
    common = common.replace(old_default, new_default, 1)
    common_config.write_text(common, encoding="utf-8")

forge = forge_config.read_text(encoding="utf-8")
old_comment = (
    '                                "Use a 26.3-style cache-aware bulk-fill path for Minecraft 1.20.1 Cache2D density functions. " +\n'
    '                                "This keeps double-precision world generation and changes no density formula; it prevents " +\n'
    '                                "bulk fills from bypassing the existing X/Z cache. Automatically yielded to HariChunk/C2ME.")\n'
)
new_comment = (
    '                                "EXPERIMENTAL: use a 26.3-style cache-aware bulk-fill path for Minecraft 1.20.1 Cache2D density functions. " +\n'
    '                                "The 2.2.0 real-Forge ABBA lab did not meet the default-on performance threshold, so this remains OFF by default. " +\n'
    '                                "It keeps double-precision density math and automatically yields to HariChunk/C2ME.")\n'
)
if new_comment not in forge:
    count = forge.count(old_comment)
    if count != 1:
        raise SystemExit(f"source drift: expected one Cache2D config comment, found {count}")
    forge = forge.replace(old_comment, new_comment, 1)
    forge_config.write_text(forge, encoding="utf-8")

final_common = common_config.read_text(encoding="utf-8")
final_forge = forge_config.read_text(encoding="utf-8")
if 'enableMc263DensityCacheFill", false' not in final_common:
    raise SystemExit("release policy invariant failed: Cache2D lane is not default OFF")
if "did not meet the default-on performance threshold" not in final_forge:
    raise SystemExit("release policy invariant failed: experimental Cache2D rationale missing")

print("HariMultiThread 2.2.0 MC26.3 release policy applied: Cache2D lane experimental/OFF by default")
