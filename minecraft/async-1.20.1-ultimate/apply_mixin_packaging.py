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

# Fail closed on upstream/source drift. This layer owns production Forge Mixin
# discovery AND refmap generation; silently stacking a second configuration can
# create duplicate AP arguments or a manifest which differs from the tested JAR.
for owned in (
    "'MixinConfigs'",
    '"MixinConfigs"',
    "apply plugin: 'org.spongepowered.mixin'",
    "org.spongepowered:mixingradle:",
    "org.spongepowered:mixin:${mixin_version}:processor",
    'add sourceSets.main, "${mod_id}.refmap.json"',
):
    if owned in text:
        raise SystemExit(f"source drift: Forge build already contains production Mixin packaging token: {owned}")

# ForgeGradle 6 does not generate a Mixin refmap merely because the config JSON
# names one. MixinGradle wires the Mixin AP to the Forge compile task and adds
# the generated refmap to compiler outputs. Use the standard FG3+ MixinGradle
# line, with the Sponge repository scoped to buildscript resolution only.
plugins_marker = "plugins {\n"
if text.count(plugins_marker) != 1:
    raise SystemExit("source drift: expected exactly one Forge plugins block")

buildscript = '''buildscript {\n    repositories {\n        maven { url = 'https://repo.spongepowered.org/repository/maven-public' }\n        mavenCentral()\n    }\n    dependencies {\n        classpath 'org.spongepowered:mixingradle:0.7-SNAPSHOT'\n    }\n}\n\n'''
text = text.replace(plugins_marker, buildscript + plugins_marker, 1)

plugins_close_marker = "}\n\njava.toolchain.languageVersion = JavaLanguageVersion.of(java_version)"
if text.count(plugins_close_marker) != 1:
    raise SystemExit("source drift: Forge plugins/toolchain boundary changed")
text = text.replace(
    plugins_close_marker,
    "}\n\napply plugin: 'org.spongepowered.mixin'\n\njava.toolchain.languageVersion = JavaLanguageVersion.of(java_version)",
    1,
)

ap_marker = '    implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:${mixinextras_version}"))\n'
if text.count(ap_marker) != 1:
    raise SystemExit("source drift: expected one MixinExtras annotation-processor dependency")
text = text.replace(
    ap_marker,
    ap_marker + '    annotationProcessor "org.spongepowered:mixin:${mixin_version}:processor"\n',
    1,
)

repositories_marker = "}\n\nrepositories {"
if text.count(repositories_marker) != 1:
    raise SystemExit("source drift: expected one minecraft/repositories boundary")
refmap_block = '''}\n\n// Production Forge refmap generation. Common Java is merged into this compile\n// task by multiloader-loader, so the main Forge source set is the authoritative\n// AP output for both common and Forge mixins in the final reobfuscated JAR.\nmixin {\n    add sourceSets.main, "${mod_id}.refmap.json"\n}\n\nrepositories {'''
text = text.replace(repositories_marker, refmap_block, 1)

# ForgeGradle dev runs pass --mixin.config explicitly, but production
# ModLauncher discovers core Mixin configs from the JAR manifest. Keep an
# explicit value on the real jar task and verify the reobfuscated output in CI.
reobf_marker = "// FG6 automatically creates the reobfJar task for SRG reobfuscation\njar.finalizedBy('reobfJar')"
if text.count(reobf_marker) != 1:
    raise SystemExit("source drift: expected exactly one Forge reobfJar marker")
manifest_block = '''// Production ModLauncher Mixin discovery.\ntasks.named('jar').configure {\n    manifest {\n        attributes([\n            'MixinConfigs': 'harimt.common.mixins.json,harimt.forge.mixins.json'\n        ])\n    }\n}\n\n'''
text = text.replace(reobf_marker, manifest_block + reobf_marker, 1)

build.write_text(text, encoding="utf-8")
print("HariMultiThread production Mixin manifest + Forge refmap generation applied successfully")
