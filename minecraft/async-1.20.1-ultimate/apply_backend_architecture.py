#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: apply_backend_architecture.py <merged-upstream-root> <backend-overlay-root>")

root = Path(sys.argv[1]).resolve()
overlay = Path(sys.argv[2]).resolve()


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

# New pure-Java/LWJGL child project. It compiles against the tiny parent API but
# is not a Forge mod and is never exposed to ModLauncher's module layer.
replace("settings.gradle", "include('forge')", "include('forge')\ninclude('vulkan-backend')")

forge = file("forge/build.gradle")
text = forge.read_text(encoding="utf-8")
marker = "// HariMultiThread Ultimate isolated backend packaging"
if marker in text:
    raise SystemExit("backend packaging block already present unexpectedly")
text += '''

// HariMultiThread Ultimate isolated backend packaging
// The backend JAR is an inert resource inside the Forge mod. VkRuntime extracts
// and child-loads it only when the GPU subsystem initializes.
def harimtVulkanBackendJar = project(':vulkan-backend').tasks.named('jar')
tasks.named('processResources') {
    dependsOn harimtVulkanBackendJar
    from(harimtVulkanBackendJar.flatMap { it.archiveFile }) {
        into 'META-INF/harimt-libs'
    }
}
'''
forge.write_text(text, encoding="utf-8")

# Remove the reflection-based Vulkan implementation entirely. Leaving dead code
# here would let future maintainers accidentally route around the compiled backend.
for rel in (
    "common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkDeviceManager.java",
    "common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkBufferManager.java",
    "common/src/main/java/com/axalotl/async/common/gpu/vulkan/VkComputePipeline.java",
):
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"expected legacy Vulkan source missing before replacement: {rel}")
    p.unlink()

# Parent replacements are applied last, after every older HMT/Noxviola patch has
# had a chance to run against the pinned upstream source it expects.
for src in overlay.rglob("*"):
    if not src.is_file():
        continue
    rel = src.relative_to(overlay)
    dst = root / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)

# Fail closed if any parent source still references the deleted reflection stack.
for p in (root / "common/src/main/java").rglob("*.java"):
    text = p.read_text(encoding="utf-8")
    for forbidden in ("VkDeviceManager", "VkBufferManager", "VkComputePipeline"):
        if forbidden in text:
            raise SystemExit(f"legacy Vulkan reference survived in {p.relative_to(root)}: {forbidden}")

print("Compile-checked isolated Vulkan backend architecture applied successfully")
