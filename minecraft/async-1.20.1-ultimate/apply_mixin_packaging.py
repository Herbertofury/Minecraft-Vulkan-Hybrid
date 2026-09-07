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
# discovery, refmap generation, and the ordering which contributes AP-generated
# hard-reference mappings to ForgeGradle's reobfuscation tasks.
for owned in (
    "'MixinConfigs'",
    '"MixinConfigs"',
    "id 'org.spongepowered.mixin'",
    "apply plugin: 'org.spongepowered.mixin'",
    "org.spongepowered:mixingradle:",
    "org.spongepowered:mixin:${mixin_version}:processor",
    'add sourceSets.main, "${mod_id}.refmap.json"',
    "configureReobfTaskForReobfJar",
    "configureReobfTaskForReobfJarJar",
):
    if owned in text:
        raise SystemExit(f"source drift: Forge build already contains production Mixin packaging token: {owned}")

# This multiloader uses exclusive repositories in settings.pluginManagement,
# so project-level buildscript.repositories are forbidden by Gradle 8.11. Use
# the official plugins-DSL form of MixinGradle instead. The settings file already
# exposes Gradle Plugin Portal plus Sponge's plugin repository.
forge_plugin = "    id 'net.minecraftforge.gradle'\n"
if text.count(forge_plugin) != 1:
    raise SystemExit("source drift: expected exactly one ForgeGradle plugin line")
text = text.replace(
    forge_plugin,
    forge_plugin + "    id 'org.spongepowered.mixin' version '0.7.+'\n",
    1,
)

# The Mixin annotation processor owns obfuscation mapping/refmap generation.
ap_marker = '    implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:${mixinextras_version}"))\n'
if text.count(ap_marker) != 1:
    raise SystemExit("source drift: expected one MixinExtras annotation-processor dependency")
text = text.replace(
    ap_marker,
    ap_marker + '    annotationProcessor "org.spongepowered:mixin:${mixin_version}:processor"\n',
    1,
)

# MixinGradle wires the AP to Forge's main Java compile and adds the generated
# refmap to compiler outputs. multiloader-loader feeds common Java into the same
# compileJava task, so this one refmap covers both common and Forge mixins.
# Register each config through MixinGradle as well: ForgeGradle dev runs then get
# one --mixin.config argument per resource, while MixinGradle contributes the
# production MixinConfigs manifest attribute itself. Do not hand-write a combined
# manifest entry into the dev launch, where ModLauncher can treat it as one literal
# resource name instead of two configuration resources.
repositories_marker = "}\n\nrepositories {"
if text.count(repositories_marker) != 1:
    raise SystemExit("source drift: expected one minecraft/repositories boundary")
refmap_block = '''}\n\n// Production Forge refmap + Mixin config registration.\nmixin {\n    add sourceSets.main, "${mod_id}.refmap.json"\n    config 'harimt.common.mixins.json'\n    config 'harimt.forge.mixins.json'\n}\n// Expected production JAR manifest contribution from MixinGradle:\n// 'MixinConfigs': 'harimt.common.mixins.json,harimt.forge.mixins.json'\n\nrepositories {\n    maven { name = 'Sponge'; url = 'https://repo.spongepowered.org/repository/maven-public' }'''
text = text.replace(repositories_marker, refmap_block, 1)

reobf_marker = "// FG6 automatically creates the reobfJar task for SRG reobfuscation\njar.finalizedBy('reobfJar')"
if text.count(reobf_marker) != 1:
    raise SystemExit("source drift: expected exactly one Forge reobfJar marker")

# MixinGradle 0.7 creates configureReobfTaskForReobf* tasks which read the
# AP-generated compileJava-mappings.tsrg. With ForgeGradle 6 Gradle may schedule
# those configuration tasks before compileJava; MixinGradle then sees no file
# and silently skips the hard-reference mappings. The resulting production JAR
# keeps Mojmap @Shadow field names and crashes against Forge's SRG runtime.
# Force the established ordering: compile first, then contribute mappings, then
# let reobfJar/reobfJarJar consume them. This repairs all generated hard refs at
# the pipeline boundary instead of adding per-field aliases.
reobf_order_block = '''// Ensure Mixin AP hard-reference mappings exist before reobf configuration.\nafterEvaluate {\n    tasks.configureReobfTaskForReobfJar.mustRunAfter(tasks.compileJava)\n    tasks.configureReobfTaskForReobfJarJar.mustRunAfter(tasks.compileJava)\n}\n\n'''
text = text.replace(reobf_marker, reobf_order_block + reobf_marker, 1)

build.write_text(text, encoding="utf-8")
print("HariMultiThread production Mixin plugin/refmap/config/reobf ordering applied successfully")
