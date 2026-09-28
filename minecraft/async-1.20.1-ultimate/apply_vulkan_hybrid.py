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
hari_window_mixin = forge_java / "com/axalotl/async/forge/mixin/client/MixinWindow.java"

required = [
    vk / "src/main/java/net/vulkanmod/Initializer.java",
    vk / "src/main/java/net/vulkanmod/mixin/MixinPlugin.java",
    vk / "src/main/resources/vulkanmod.mixins.json",
    vk / "src/main/resources/META-INF/accesstransformer.cfg",
    root / "gradle.properties",
    common_at, forge_build, mods_toml, async_forge, hari_window_mixin,
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
    # Hari owns the final mod descriptor, resource-pack metadata, and canonical
    # access transformer. Vulkan AT rules are merged into common_at below; copying
    # these root files into Forge resources would create duplicate merged resources.
    if rel.as_posix() in {
        "META-INF/mods.toml",
        "META-INF/accesstransformer.cfg",
        "pack.mcmeta",
    }:
        continue
    dst = forge_res / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)

# Fix upstream Linux platform detection before compilation. Forge/launcher and
# headless QA environments can provide a perfectly valid X11/Wayland display
# without XDG_SESSION_TYPE. Upstream treats that case as GLFW_ANY_PLATFORM
# ("ANDROID"), which prevents the desktop Vulkan renderer from initializing.
platform = dst_java / "config/Platform.java"
platform_text = platform.read_text(encoding="utf-8")
old_detect = """    private static int determineDisplayServer() {

        //Return Null platform if not on Linux (i.e. no X11 or Wayland)
        String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
        if (xdgSessionType == null) return GLFW_ANY_PLATFORM; //Likely Android
        return switch (xdgSessionType) {
            case "wayland" -> GLFW_PLATFORM_WAYLAND; //Wayland
            case "x11" -> GLFW_PLATFORM_X11; //X11
            default -> GLFW_ANY_PLATFORM; //Either unknown Platform or Display Server
        };
    }
"""
new_detect = """    private static int determineDisplayServer() {
        String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
        if (xdgSessionType != null) {
            if ("wayland".equalsIgnoreCase(xdgSessionType)) return GLFW_PLATFORM_WAYLAND;
            if ("x11".equalsIgnoreCase(xdgSessionType)) return GLFW_PLATFORM_X11;
        }

        // Launchers, Xvfb, containers and some display managers omit
        // XDG_SESSION_TYPE even though a real display server is available.
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        if (waylandDisplay != null && !waylandDisplay.isBlank()) return GLFW_PLATFORM_WAYLAND;
        String x11Display = System.getenv("DISPLAY");
        if (x11Display != null && !x11Display.isBlank()) return GLFW_PLATFORM_X11;

        // Preserve upstream's Android/unknown fallback only when no desktop
        // display evidence exists.
        return GLFW_ANY_PLATFORM;
    }
"""
if platform_text.count(old_detect) != 1:
    raise SystemExit("source drift: expected upstream Platform.determineDisplayServer")
platform_text = platform_text.replace(old_detect, new_detect, 1)
platform.write_text(platform_text, encoding="utf-8")

# Minecraft ResourceLocation paths are lowercase-only. Upstream's early-Z
# terrain fragment shader uses terrain_Z.fsh and is actively requested by
# PipelineManager, so the resource pack rejects it before Vulkan can consume it.
upper_terrain = forge_res / "assets/vulkanmod/shaders/basic/terrain/terrain_Z.fsh"
lower_terrain = forge_res / "assets/vulkanmod/shaders/basic/terrain/terrain_z.fsh"
if not upper_terrain.is_file():
    raise SystemExit("source drift: missing upstream terrain_Z.fsh")
if lower_terrain.exists():
    raise SystemExit("unexpected pre-existing lowercase terrain_z.fsh")
upper_terrain.rename(lower_terrain)

pipeline_manager = dst_java / "render/PipelineManager.java"
pipeline_text = pipeline_manager.read_text(encoding="utf-8")
if pipeline_text.count('"terrain_Z"') != 1:
    raise SystemExit("source drift: expected one terrain_Z pipeline reference")
pipeline_text = pipeline_text.replace('"terrain_Z"', '"terrain_z"', 1)
pipeline_manager.write_text(pipeline_text, encoding="utf-8")

# Vulkan sources compile inside Hari's Forge source set, so the existing
# harimt.refmap.json annotation-processor output already contains the merged
# net.vulkanmod mappings. Point Vulkan's config at that shared generated refmap
# instead of the standalone fork's vulkanmod.refmap.json (which does not exist
# in the merged artifact).
vulkan_mixins = forge_res / "vulkanmod.mixins.json"
vulkan_mixins_text = vulkan_mixins.read_text(encoding="utf-8")
if '"refmap": "vulkanmod.refmap.json"' not in vulkan_mixins_text:
    raise SystemExit("source drift: expected standalone Vulkan refmap declaration")
vulkan_mixins_text = vulkan_mixins_text.replace(
    '"refmap": "vulkanmod.refmap.json"',
    '"refmap": "harimt.refmap.json"',
    1,
)
vulkan_mixins.write_text(vulkan_mixins_text, encoding="utf-8")

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

# Hari's persistent GPU-scene bridge probes OpenGL capabilities at Window
# constructor return. The Vulkan WindowMixin deliberately leaves that window
# contextless, so GL.getCapabilities() is invalid in Vulkan mode. Keep Hari's
# detector unchanged for every OpenGL fallback lane and skip only this call when
# the session renderer gate selected Vulkan.
hari_window_text = hari_window_mixin.read_text(encoding="utf-8")
gpu_probe = "HariRenderState.detectCapabilities();"
if hari_window_text.count(gpu_probe) != 1:
    raise SystemExit("source drift: expected exactly one Hari Window GPU capability probe")
hari_window_text = hari_window_text.replace(
    gpu_probe,
    """if (!net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()) {
            HariRenderState.detectCapabilities();
        }""",
    1,
)
hari_window_mixin.write_text(hari_window_text, encoding="utf-8")

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

# Register Vulkan through MixinGradle, not a raw runClient argument. The existing
# MixinGradle source-set registration owns harimt.refmap.json and ForgeGradle's
# mapped-dev remapping lifecycle. A manual --mixin.config argument loads the
# config but bypasses that lifecycle, producing "No refMap loaded" and SRG
# @Shadow failures in runClient even though the packaged Forge client is valid.
mixin_anchor = """mixin {
    add sourceSets.main, "${mod_id}.refmap.json"
    config 'harimt.common.mixins.json'
    config 'harimt.forge.mixins.json'
"""
if mixin_anchor not in build:
    raise SystemExit("source drift: production MixinGradle block missing")
if "    config 'vulkanmod.mixins.json'\n" not in build:
    build = build.replace(
        mixin_anchor,
        mixin_anchor + "    config 'vulkanmod.mixins.json'\n",
        1,
    )

# Ensure the stale raw dev-run registration is absent.
if '--mixin.config=vulkanmod.mixins.json' in build:
    raise SystemExit("stale raw Vulkan Mixin dev-run argument survived")

marker = "// Hari 2.4 merged Vulkan renderer"
if marker not in build:
    build += r"""

// Hari 2.4 merged Vulkan renderer
def harimtLwjglVersion = '3.3.3'
def harimtMergedMixinConfigs = 'harimt.common.mixins.json,harimt.forge.mixins.json,harimt.gpu.mixins.json,vulkanmod.mixins.json'

// Forge 1.20.1 discovers Mixin configs from the distributable JAR manifest.
// Hari's reconstructed manifest already owns the three Hari configs; the merged
// Vulkan renderer must be registered there too or its window/render mixins are
// packaged but never applied at runtime.
tasks.named('jar').configure {
    manifest {
        attributes 'MixinConfigs': harimtMergedMixinConfigs
    }
}


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
    harimtVulkanNativesMacos "org.lwjgl:lwjgl-vulkan:" + harimtLwjglVersion + ":natives-macos"
    harimtVulkanNativesMacosArm64 "org.lwjgl:lwjgl-shaderc:" + harimtLwjglVersion + ":natives-macos-arm64"
    harimtVulkanNativesMacosArm64 "org.lwjgl:lwjgl-vma:" + harimtLwjglVersion + ":natives-macos-arm64"
    harimtVulkanNativesMacosArm64 "org.lwjgl:lwjgl-vulkan:" + harimtLwjglVersion + ":natives-macos-arm64"
}

// Match the Forge port's split-LWJGL contract: embed only the Vulkan-side modules.
// Minecraft keeps ownership of core LWJGL/GLFW/OpenGL/STB to avoid classpath replacement.
configurations.jarJar {
    exclude group: 'org.lwjgl', module: 'lwjgl'
    exclude group: 'org.lwjgl', module: 'lwjgl-glfw'
    exclude group: 'org.lwjgl', module: 'lwjgl-opengl'
    exclude group: 'org.lwjgl', module: 'lwjgl-stb'
}

// Stage bundled shaderc/VMA natives as normal resources so ForgeGradle runClient
// and the shipped JAR exercise the exact same resource layout. The production
// server collision shader is pinned in the overlay; CI recompiles GLSL and
// byte-compares it to prove source/binary parity.
def harimtCollisionSpirv = rootProject.file('common/src/main/resources/assets/async/shaders/collision_broadphase.comp.spv')
tasks.named('processResources').configure {
    inputs.file(harimtCollisionSpirv)
    doFirst {
        if (!harimtCollisionSpirv.isFile() || harimtCollisionSpirv.length() < 4L) {
            throw new GradleException('Missing production collision SPIR-V: ' + harimtCollisionSpirv)
        }
        def bytes = harimtCollisionSpirv.bytes
        def magic = [0, 1, 2, 3].collect { ((int) bytes[it]) & 0xff }
        if (magic != [0x03, 0x02, 0x23, 0x07]) {
            throw new GradleException('Invalid collision SPIR-V magic: ' + magic)
        }
    }
    into('assets/vulkanmod/natives/windows/x64') {
        from { configurations.harimtVulkanNativesWindows.collect { zipTree(it) } }
        include 'windows/x64/org/lwjgl/**'
    }
    into('assets/vulkanmod/natives/linux/x64') {
        from { configurations.harimtVulkanNativesLinux.collect { zipTree(it) } }
        include 'linux/x64/org/lwjgl/**'
    }
    into('assets/vulkanmod/natives/linux/arm64') {
        from { configurations.harimtVulkanNativesLinuxArm64.collect { zipTree(it) } }
        include 'linux/arm64/org/lwjgl/**'
    }
    into('assets/vulkanmod/natives/macos/x64') {
        from { configurations.harimtVulkanNativesMacos.collect { zipTree(it) } }
        include 'macos/x64/org/lwjgl/**'
    }
    into('assets/vulkanmod/natives/macos/arm64') {
        from { configurations.harimtVulkanNativesMacosArm64.collect { zipTree(it) } }
        include 'macos/arm64/org/lwjgl/**'
    }
}

// Forge's Jar-in-Jar module layer cannot resolve LWJGL 3.3.3's Java-9 module
// descriptors against Minecraft's launcher-owned org.lwjgl core module. The
// pinned Forge renderer solves this by stripping module-info from every nested
// LWJGL jar while leaving the normal classes intact. Preserve that exact split:
// Minecraft owns core LWJGL; Hari owns only Vulkan/shaderc/VMA as JIJ metadata.
tasks.named('jarJar').configure {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    doFirst {
        def mixinConfigs = tasks.named('jar').get().manifest.attributes.get('MixinConfigs')?.toString()
        if (mixinConfigs == null || !mixinConfigs.split(',').collect { it.trim() }.contains('vulkanmod.mixins.json')) {
            throw new GradleException('Merged Vulkan renderer missing from JAR MixinConfigs manifest: ' + mixinConfigs)
        }
    }
    exclude 'module-info.class'
    exclude 'META-INF/versions/*/module-info.class'
    exclude 'META-INF/*.SF'
    exclude 'META-INF/*.DSA'
    exclude 'META-INF/*.RSA'

    doLast {
        def jarFile = archiveFile.get().asFile
        def tmpDir = new File(temporaryDir, 'harimt-lwjgl-repack')
        tmpDir.deleteDir()
        tmpDir.mkdirs()
        ant.unzip(src: jarFile, dest: tmpDir)

        def jarjarDir = new File(tmpDir, 'META-INF/jarjar')
        if (jarjarDir.exists()) {
            jarjarDir.listFiles().findAll {
                it.name.endsWith('.jar') && it.name.startsWith('lwjgl-')
            }.each { embeddedJar ->
                def stripped = new File(temporaryDir, "stripped-${embeddedJar.name}")
                ant.zip(destfile: stripped) {
                    zipfileset(src: embeddedJar) {
                        exclude(name: 'module-info.class')
                        exclude(name: 'META-INF/versions/*/module-info.class')
                    }
                }
                if (!embeddedJar.delete()) {
                    throw new GradleException("Could not replace nested LWJGL jar: ${embeddedJar}")
                }
                if (!stripped.renameTo(embeddedJar)) {
                    throw new GradleException("Could not install stripped nested LWJGL jar: ${embeddedJar}")
                }
            }
        }

        // JarJar owns its own final manifest snapshot. Rewrite that exact
        // distributable manifest during the same deterministic repack that strips
        // nested LWJGL module descriptors, so the final shipped bytes cannot lose
        // the merged Vulkan Mixin registration even if Gradle task-manifest
        // inheritance changes.
        def manifestFile = new File(tmpDir, 'META-INF/MANIFEST.MF')
        if (!manifestFile.isFile()) {
            throw new GradleException('Final JarJar staging tree is missing META-INF/MANIFEST.MF')
        }
        def mergedManifest
        manifestFile.withInputStream { input ->
            mergedManifest = new java.util.jar.Manifest(input)
        }
        mergedManifest.mainAttributes.putValue('MixinConfigs', harimtMergedMixinConfigs)
        manifestFile.withOutputStream { output ->
            mergedManifest.write(output)
        }

        if (!jarFile.delete()) {
            throw new GradleException("Could not replace jarJar output: ${jarFile}")
        }
        ant.zip(destfile: jarFile, basedir: tmpDir)

        // Verify the exact distributable bytes, not just Gradle task state.
        def finalZip = new java.util.zip.ZipFile(jarFile)
        try {
            def manifestEntry = finalZip.getEntry('META-INF/MANIFEST.MF')
            def configEntry = finalZip.getEntry('vulkanmod.mixins.json')
            def refmapEntry = finalZip.getEntry('harimt.refmap.json')
            if (manifestEntry == null || configEntry == null || refmapEntry == null) {
                throw new GradleException('Merged Vulkan mixin packaging incomplete in final jarJar output')
            }

            def manifestText = finalZip.getInputStream(manifestEntry).getText('UTF-8')
                    .replaceAll('\\r?\\n ', '')
            if (!manifestText.contains('vulkanmod.mixins.json')) {
                throw new GradleException('Final JAR manifest does not register vulkanmod.mixins.json')
            }

            def configText = finalZip.getInputStream(configEntry).getText('UTF-8')
            if (!configText.contains('"refmap": "harimt.refmap.json"')) {
                throw new GradleException('Final Vulkan mixin config does not use merged harimt.refmap.json')
            }

            def refmapText = finalZip.getInputStream(refmapEntry).getText('UTF-8')
            if (!refmapText.contains('net/vulkanmod/mixin/')) {
                throw new GradleException('Merged harimt.refmap.json contains no Vulkan mixin mappings')
            }
        } finally {
            finalZip.close()
        }
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

assert not (forge_res / "META-INF/accesstransformer.cfg").exists(), "duplicate Forge AT resource present"
assert not (forge_res / "pack.mcmeta").exists(), "duplicate Forge pack.mcmeta present"
assert any("net.vulkanmod" in line or "com.mojang" in line or "net.minecraft" in line for line in vk_lines), "Vulkan AT rules unexpectedly empty"
assert '@Mod("vulkanmod")' not in init.read_text(encoding="utf-8")
assert 'UniversalRendererGate.vulkanRendererEnabled()' in plugin.read_text(encoding="utf-8")
assert 'net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()' in hari_window_mixin.read_text(encoding="utf-8")
assert hari_window_mixin.read_text(encoding="utf-8").count('HariRenderState.detectCapabilities();') == 1
assert 'new net.vulkanmod.Initializer()' in async_forge.read_text(encoding="utf-8")
assert 'FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT' in async_forge.read_text(encoding="utf-8")
assert 'config = "vulkanmod.mixins.json"' in mods_toml.read_text(encoding="utf-8")
assert '"refmap": "harimt.refmap.json"' in vulkan_mixins.read_text(encoding="utf-8")
assert '"refmap": "vulkanmod.refmap.json"' not in vulkan_mixins.read_text(encoding="utf-8")
final_build_text = forge_build.read_text(encoding="utf-8")
assert "harimt.gpu.mixins.json,vulkanmod.mixins.json" in final_build_text
assert "mergedManifest.mainAttributes.putValue('MixinConfigs', harimtMergedMixinConfigs)" in final_build_text
assert "config 'vulkanmod.mixins.json'" in final_build_text
assert final_build_text.count("config 'vulkanmod.mixins.json'") == 1
assert '--mixin.config=vulkanmod.mixins.json' not in final_build_text
assert "Merged Vulkan renderer missing from JAR MixinConfigs manifest" in final_build_text
assert contract_path.stat().st_size > 128
assert lower_terrain.is_file() and not upper_terrain.exists()
assert '"terrain_z"' in pipeline_manager.read_text(encoding="utf-8")
assert '"terrain_Z"' not in pipeline_manager.read_text(encoding="utf-8")
assert 'System.getenv("DISPLAY")' in platform.read_text(encoding="utf-8")
assert 'System.getenv("WAYLAND_DISPLAY")' in platform.read_text(encoding="utf-8")
assert "version=2.4.0-noxviola.1-vulkan-hybrid" in (root / "gradle.properties").read_text(encoding="utf-8")
print("Hari 2.4 merged Vulkan renderer applied successfully")
