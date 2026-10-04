#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_lwjgl_runtime.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()


def file(rel):
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"missing merged file: {rel}")
    return p


def replace(rel, old, new, count=1):
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        raise SystemExit(f"source drift in {rel}: expected {count}, found {found}: {old[:150]!r}")
    p.write_text(text.replace(old, new, count), encoding="utf-8")

# Forge JarJar treats lwjgl-vulkan as a named module. Dedicated Forge servers do
# not provide the org.lwjgl module, so ModLauncher fails before HMT can fall back.
# Embed ordinary dependency JAR resources instead and load them through VkRuntime.
forge_gradle = "forge/build.gradle"
replace(
    forge_gradle,
'''    // 5. GPU: Bundle LWJGL Vulkan into the mod JAR via JarJar
    // Forge ships LWJGL 3.3.1 core but NOT the Vulkan module.
    implementation(jarJar("org.lwjgl:lwjgl-vulkan:[3.3.1,3.4.0)"))
    implementation "org.lwjgl:lwjgl-vulkan:3.3.1"''',
'''    // 5. GPU classes compile in common. Runtime LWJGL JARs are embedded as
    // ordinary resources below, NOT JarJar modules, so dedicated servers can
    // boot even when their module layer does not provide org.lwjgl.'''
)

replace(
    forge_gradle,
'''repositories {
    maven { name = 'Bawnorton'; url = 'https://maven.bawnorton.com/releases' }
    mavenCentral()
    mavenLocal()
}
''',
'''repositories {
    maven { name = 'Bawnorton'; url = 'https://maven.bawnorton.com/releases' }
    mavenCentral()
    mavenLocal()
}

configurations {
    harimtEmbeddedLwjgl
}
'''
)

replace(
    forge_gradle,
'''    // 5. GPU classes compile in common. Runtime LWJGL JARs are embedded as
    // ordinary resources below, NOT JarJar modules, so dedicated servers can
    // boot even when their module layer does not provide org.lwjgl.
}''',
'''    // 5. GPU classes compile in common. Runtime LWJGL JARs are embedded as
    // ordinary resources below, NOT JarJar modules, so dedicated servers can
    // boot even when their module layer does not provide org.lwjgl.
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl-vulkan:3.3.1") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-linux") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-linux-arm32") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-linux-arm64") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-macos") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-macos-arm64") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-windows") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-windows-arm64") { transitive = false }
    harimtEmbeddedLwjgl("org.lwjgl:lwjgl:3.3.1:natives-windows-x86") { transitive = false }
}

tasks.named('processResources') {
    from(configurations.harimtEmbeddedLwjgl) {
        into 'META-INF/harimt-libs'
    }
}'''
)

# All LWJGL/Vulkan types are already reached reflectively. Route those lookups
# through VkRuntime so the client can reuse launcher LWJGL while a dedicated
# server can load the embedded child-classloader runtime.
for rel in (
    "common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkDeviceManager.java",
    "common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkBufferManager.java",
    "common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkComputePipeline.java",
):
    p = file(rel)
    text = p.read_text(encoding="utf-8")
    count = text.count("Class.forName(")
    if count == 0:
        raise SystemExit(f"source drift in {rel}: no Class.forName lookups found")
    text = text.replace("Class.forName(", "VkRuntime.load(")
    p.write_text(text, encoding="utf-8")
    print(f"routed {count} reflective class lookups through VkRuntime in {rel}")

print("HariMultiThread Ultimate isolated LWJGL runtime hardening applied successfully")
