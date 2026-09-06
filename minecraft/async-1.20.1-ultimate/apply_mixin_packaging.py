#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mixin_packaging.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
build = root / "forge/build.gradle"
if not build.is_file():
    raise SystemExit(f"missing merged Forge build file: {build}")

text = build.read_text(encoding="utf-8")

if "'MixinConfigs'" in text or '"MixinConfigs"' in text:
    raise SystemExit("source drift: Forge build already declares MixinConfigs")

marker = "// FG6 automatically creates the reobfJar task for SRG reobfuscation\njar.finalizedBy('reobfJar')"
if text.count(marker) != 1:
    raise SystemExit(
        "source drift: expected exactly one Forge reobfJar marker before adding production mixin manifest"
    )

manifest_block = '''// ForgeGradle dev runs pass --mixin.config explicitly, but production\n// ModLauncher discovers core mixin configs from the JAR manifest. Keep this\n// declaration on the real jar task so it survives reobfuscation into the\n// packaged Forge artifact.\ntasks.named('jar').configure {\n    manifest {\n        attributes([\n            'MixinConfigs': 'harimt.common.mixins.json,harimt.forge.mixins.json'\n        ])\n    }\n}\n\n'''

text = text.replace(marker, manifest_block + marker, 1)
build.write_text(text, encoding="utf-8")

print("HariMultiThread production MixinConfigs manifest packaging applied successfully")
