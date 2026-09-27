#!/usr/bin/env python3
"""Merge the pinned Forge 1.20.1 Vulkan renderer into reconstructed HariMultiThread 2.4.

Usage:
  apply_vulkan_hybrid.py <hari-root> <forgified-vulkanmod-root>

The Vulkan source is compiled into Hari's Forge artifact as one logical mod. Vulkan's own
@Mod declaration and mods.toml are deliberately not carried over. Its mixins are always
registered, but the plugin is fail-closed through UniversalRendererGate so incompatible
packs boot Hari's proven OpenGL fallback instead of applying Vulkan transforms.
"""
from __future__ import annotations

from pathlib import Path
import re
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: apply_vulkan_hybrid.py <hari-root> <forgified-vulkanmod-root>")

root = Path(sys.argv[1]).resolve()
vk = Path(sys.argv[2]).resolve()
here = Path(__file__).resolve().parent

forge_java = root / "forge/src/main/java"
forge_res = root / "forge/src/main/resources"
common_at = root / "common/src/main/resources/META-INF/accesstransformer.cfg"
forge_build = root / "forge/build.gradle"
mods_toml = forge_res / "META-INF/mods.toml"
async_forge = forge_java / "com/axalotl/async/forge/AsyncForge.java"

required = [
    vk / "src/main/java/net/vulkanmod/Initializer.java",
    vk / "src/main/java/net/vulkanmod/mixin/MixinPlugin.java",
    vk / "src/main/resources/vulkanmod.mixins.json",
    vk / "src/main/resources/META-INF/accesstransformer.cfg",
    root / "gradle.properties",
    common_at, forge_build, mods_toml, async_forge,
]
for p in required:
    if not p.is_file():
        raise SystemExit(f"missing merge input: {p}")

props = (root / "gradle.properties").read_text(encoding="utf-8")
if "version=2.3.0-noxviola.1" not in props:
    raise SystemExit("source drift: expected reconstructed Hari 2.3 persistent-scene base")

src_java = vk / "src/main/java/net/vulkanmod"
dst_java = forge_java / "net/vulkanmod"
if dst_java.exists():
    shutil.rmtree(dst_java)
shutil.copytree(src_java, dst_java)

src_res = vk / "src/main/resources"
for src in src_res.rglob("*"):
    if not src.is_file():
        continue
    rel = src.relative_to(src_res)
    if rel.as_posix() == "META-INF/mods.toml":
        continue
    dst = forge_res / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)

prov = forge_res / "META-INF/harimt-vulkan"
prov.mkdir(parents=True, exist_ok=True)
for name in ("LICENSE", "COPYING", "NOTICE", "README.md"):
    src = vk / name
    if src.is_file():
        shutil.copy2(src, prov / f"Forgified-VulkanMod-{name}")
(prov / "SOURCE.txt").write_text(
    "Merged renderer source:\n"
    "  xCollateral/VulkanMod (upstream Vulkan renderer)\n"
    "  kzktor/Forgified-VulkanMod Forge 1.20.1 integration\n"
    "Pinned integration source commit: 0ceac5d47f84c910d2f5f6d007b0ffe266c6f736\n"
    "Hari integration branch: async-1.20.1-ultimate-2.4.0-vulkan-hybrid-20260926\n"
    "Changes: merged into one logical Hari mod; fail-closed runtime renderer gate; "
    "Hari OpenGL fallback retained; Vulkan compute backend retained.\n",
    encoding="utf-8",
)

init = dst_java / "Initializer.java"
text = init.read_text(encoding="utf-8")
# Hari is the only logical Forge mod in the merged artifact. Remove VulkanMod's
# independent entrypoint annotation/import but keep its bootstrap class callable.
text = text.replace("import net.minecraftforge.fml.common.Mod;\n", "")
text = text.replace('@Mod("vulkanmod")\n', "")
# The standalone fork checks TrulyRin/VulkanMod-Reforged for updates. Once merged
# into Hari that version comparison is wrong (Hari's 2.4 version is not a VulkanMod
# release) and adds needless startup network I/O. Hari owns updates for this single mod.
text = text.replace("import net.vulkanmod.config.UpdateChecker;\n", "")
text = re.sub(r"^\s*UpdateChecker\.checkForUpdates\(\);\s*$", "", text, flags=re.MULTILINE)
if '@Mod("vulkanmod")' in text:
    raise SystemExit("failed to remove VulkanMod independent @Mod declaration")
if "UpdateChecker.checkForUpdates()" in text:
    raise SystemExit("failed to remove standalone VulkanMod update checker")
init.write_text(text, encoding="utf-8")

gate_src = here / "vulkan-hybrid-src/net/vulkanmod/compat/UniversalRendererGate.java"
gate_dst = dst_java / "compat/UniversalRendererGate.java"
if not gate_src.is_file():
    raise SystemExit("missing UniversalRendererGate source")
gate_dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(gate_src, gate_dst)

plugin = dst_java / "mixin/MixinPlugin.java"
text = plugin.read_text(encoding="utf-8")
anchor = "    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {\n"
insert = anchor + "        if (!net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()) {\n" \
                  "            return false;\n" \
                  "        }\n"
if anchor not in text:
    raise SystemExit("source drift: Vulkan MixinPlugin shouldApplyMixin anchor missing")
text = text.replace(anchor, insert, 1)
plugin.write_text(text, encoding="utf-8")

policy = dst_java / "compat/CompatPolicyManager.java"
text = policy.read_text(encoding="utf-8")
old = """        if (category == CompatCategory.RENDERER_GL) {
            return CompatMode.INCOMPATIBLE;
        }
"""
if old in text:
    text = text.replace(old, """        if (category == CompatCategory.RENDERER_GL) {
            return UniversalRendererGate.vulkanRendererEnabled() ? CompatMode.INCOMPATIBLE : CompatMode.OFF;
        }
""", 1)
policy.write_text(text, encoding="utf-8")

# Generate an exact direct-call compatibility contract from the imported GL overwrite surface.
gl_dir = dst_java / "mixin/compatibility/gl"
contracts = {}
mixin_re = re.compile(r"@Mixin\(\s*(\w+)\.class\s*\)")
method_re = re.compile(
    r"@Overwrite(?:\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"public\s+static\s+[\w<>\[\]?., ]+\s+(\w+)\s*\(",
    re.MULTILINE,
)
for source in sorted(gl_dir.glob("*.java")):
    body = source.read_text(encoding="utf-8")
    mixin = mixin_re.search(body)
    if not mixin:
        continue
    owner = mixin.group(1)
    methods = set(method_re.findall(body))
    if methods:
        contracts.setdefault(owner, set()).update(methods)
if not contracts:
    raise SystemExit("failed to derive Vulkan OpenGL compatibility contract")
contract_path = forge_res / "assets/vulkanmod/compat/harimt_supported_gl_methods.properties"
contract_path.parent.mkdir(parents=True, exist_ok=True)
contract_path.write_text(
    "# Generated from Forgified-VulkanMod compatibility @Overwrite methods.\n" +
    "\n".join(f"{owner}=" + ",".join(sorted(methods)) for owner, methods in sorted(contracts.items())) +
    "\n",
    encoding="utf-8",
)

text = async_forge.read_text(encoding="utf-8")
anchor = '        LOGGER.info("Initializing Async...");\n'
insert = anchor + """        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            if (net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()) {
                LOGGER.info("Hari 2.4 selected merged Vulkan renderer: {}", net.vulkanmod.compat.UniversalRendererGate.reason());
                new net.vulkanmod.Initializer();
            } else {
                LOGGER.info("Hari 2.4 selected OpenGL compatibility renderer: {}", net.vulkanmod.compat.UniversalRendererGate.reason());
            }
        }
"""
if anchor not in text:
    raise SystemExit("source drift: AsyncForge constructor anchor missing")
text = text.replace(anchor, insert, 1)
async_forge.write_text(text, encoding="utf-8")

text = mods_toml.read_text(encoding="utf-8")
mixin_block = '[[mixins]]\nconfig = "vulkanmod.mixins.json"\n\n'
if 'config = "vulkanmod.mixins.json"' not in text:
    marker = '[[dependencies."' + '$' + '{mod_id}"]]\n'
    if marker not in text:
        raise SystemExit("source drift: mods.toml dependency anchor missing")
    text = text.replace(marker, mixin_block + marker, 1)
mods_toml.write_text(text, encoding="utf-8")

hari_lines = common_at.read_text(encoding="utf-8").splitlines()
vk_lines = (vk / "src/main/resources/META-INF/accesstransformer.cfg").read_text(encoding="utf-8").splitlines()
seen = set()
merged = []
for line in hari_lines + ["# Vulkan renderer access rules"] + vk_lines:
    key = line.strip()
    if key and not key.startswith("#"):
        if key in seen:
            continue
        seen.add(key)
    merged.append(line)
common_at.write_text("\n".join(merged).rstrip() + "\n", encoding="utf-8")

build = forge_build.read_text(encoding="utf-8")
marker = "// Hari 2.4 merged Vulkan renderer"
if marker not in build:
    build += r"""

// Hari 2.4 merged Vulkan renderer
def harimtLwjglVersion = '3.3.3'

configurations {
    harimtVulkanNativesWindows { canBeResolved = true; canBeConsumed = false; transitive = false }
    harimtVulkanNativesLinux { canBeResolved = true; canBeConsumed = false; transitive = false }
    harimtVulkanNativesLinuxArm64 { canBeResolved = true; canBeConsumed = false; transitive = false }
    harimtVulkanNativesMacos { canBeResolved = true; canBeConsumed = false; transitive = false }
    harimtVulkanNativesMacosArm64 { canBeResolved = true; canBeConsumed = false; transitive = false }
}

repositories {
    maven { name = 'Sinytra'; url = 'https://maven.su5ed.dev/releases' }
}

dependencies {
    // Android/FCL EGL handoff uses JNA; desktop paths do not load it.
    compileOnly "net.java.dev.jna:jna:5.13.0"

    // Keep the three Vulkan-side LWJGL modules on the exact version proven by the
    // Forge 1.20.1 renderer port. Do not replace Minecraft's core/GLFW/OpenGL modules.
    implementation "org.lwjgl:lwjgl-vulkan:" + harimtLwjglVersion
    implementation "org.lwjgl:lwjgl-shaderc:" + harimtLwjglVersion
    implementation "org.lwjgl:lwjgl-vma:" + harimtLwjglVersion
    implementation(jarJar("org.lwjgl:lwjgl-vulkan:[3.3.3,3.3.4)"))
    implementation(jarJar("org.lwjgl:lwjgl-shaderc:[3.3.3,3.3.4)"))
    implementation(jarJar("org.lwjgl:lwjgl-vma:[3.3.3,3.3.4)"))

    compileOnly fg.deobf("dev.su5ed.sinytra.fabric-api:fabric-renderer-api-v1:3.2.1+1d29b44577")
    compileOnly fg.deobf("dev.su5ed.sinytra.fabric-api:fabric-api-base:0.4.31+ef105b4977")

    harimtVulkanNativesWindows "org.lwjgl:lwjgl-shaderc:" + harimtLwjglVersion + ":natives-windows"
    harimtVulkanNativesWindows "org.lwjgl:lwjgl-vma:" + harimtLwjglVersion + ":natives-windows"
    harimtVulkanNativesLinux "org.lwjgl:lwjgl-shaderc:" + harimtLwjglVersion + ":natives-linux"
    harimtVulkanNativesLinux "org.lwjgl:lwjgl-vma:" + harimtLwjglVersion + ":natives-linux"
    harimtVulkanNativesLinuxArm64 "org.lwjgl:lwjgl-shaderc:" + harimtLwjglVersion + ":natives-linux-arm64"
    harimtVulkanNativesLinuxArm64 "org.lwjgl:lwjgl-vma:" + harimtLwjglVersion + ":natives-linux-arm64"
    harimtVulkanNativesMacos "org.lwjgl:lwjgl-shaderc:" + harimtLwjglVersion + ":natives-macos"
    harimtVulkanNativesMacos "org.lwjgl:lwjgl-vma:" + harimtLwjglVersion + ":natives-macos"
    harimtVulkanNativesMacosArm64 "org.lwjgl:lwjgl-shaderc:" + harimtLwjglVersion + ":natives-macos-arm64"
    harimtVulkanNativesMacosArm64 "org.lwjgl:lwjgl-vma:" + harimtLwjglVersion + ":natives-macos-arm64"
}

tasks.named('jar', Jar).configure {
    into('assets/vulkanmod/natives/windows/x64') {
        from { configurations.harimtVulkanNativesWindows.collect { zipTree(it) } }
    }
    into('assets/vulkanmod/natives/linux/x64') {
        from { configurations.harimtVulkanNativesLinux.collect { zipTree(it) } }
    }
    into('assets/vulkanmod/natives/linux/arm64') {
        from { configurations.harimtVulkanNativesLinuxArm64.collect { zipTree(it) } }
    }
    into('assets/vulkanmod/natives/macos/x64') {
        from { configurations.harimtVulkanNativesMacos.collect { zipTree(it) } }
    }
    into('assets/vulkanmod/natives/macos/arm64') {
        from { configurations.harimtVulkanNativesMacosArm64.collect { zipTree(it) } }
    }
}
"""
forge_build.write_text(build, encoding="utf-8")

# Build reliability: upstream declares Bawnorton's Maven as a broad repository even though the
# only dependencies that use it are commented out. A 530 from that mirror otherwise aborts
# resolution of Forge artifacts such as bootstraplauncher before Gradle reaches Forge/Maven Central.
# Remove only the unused broad repository; no dependency or feature is removed.
common_gradle = root / "buildSrc/src/main/groovy/multiloader-common.gradle"
if common_gradle.is_file():
    cg = common_gradle.read_text(encoding="utf-8")
    # Vulkan mixin JSON contains legitimate inner-class '$' names such as
    # VertexMultiConsumersM$DoubleM. Groovy template expansion treats these as
    # properties. Hari mixin JSON contains no template placeholders, so exclude
    # all *.mixins.json files from SimpleTemplateEngine expansion.
    cg = cg.replace(", '*.mixins.json'", "")
    if "'*.mixins.json'" in cg:
        raise SystemExit("failed to disable mixin JSON template expansion")
    cg = cg.replace("    maven { url = 'https://maven.bawnorton.com/releases' }\n", "")
    common_gradle.write_text(cg, encoding="utf-8")
fb = forge_build.read_text(encoding="utf-8")
fb = fb.replace("    maven { name = 'Bawnorton'; url = 'https://maven.bawnorton.com/releases' }\n", "")
forge_build.write_text(fb, encoding="utf-8")
common_build = root / "common/build.gradle"
if common_build.is_file():
    cb = common_build.read_text(encoding="utf-8")
    cb = cb.replace("    maven { name = 'Bawnorton'; url = 'https://maven.bawnorton.com/releases' }\n", "")
    common_build.write_text(cb, encoding="utf-8")

props = props.replace("version=2.3.0-noxviola.1", "version=2.4.0-noxviola.1-vulkan-hybrid", 1)
(root / "gradle.properties").write_text(props, encoding="utf-8")

assert '@Mod("vulkanmod")' not in init.read_text(encoding="utf-8")
assert 'UniversalRendererGate.vulkanRendererEnabled()' in plugin.read_text(encoding="utf-8")
assert 'new net.vulkanmod.Initializer()' in async_forge.read_text(encoding="utf-8")
assert 'FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT' in async_forge.read_text(encoding="utf-8")
assert 'config = "vulkanmod.mixins.json"' in mods_toml.read_text(encoding="utf-8")
assert contract_path.stat().st_size > 128
assert "version=2.4.0-noxviola.1-vulkan-hybrid" in (root / "gradle.properties").read_text(encoding="utf-8")
print("Hari 2.4 merged Vulkan renderer applied successfully")
