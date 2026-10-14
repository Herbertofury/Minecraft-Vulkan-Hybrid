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

# The backend source is kept outside Forge's normal main source set, but compiled
# by a dedicated pure-Java source set inside the Forge project. This avoids HMT's
# root subprojects{} block applying Architectury Loom to a fake backend project.
unused_backend_build = root / "vulkan-backend/build.gradle"
if unused_backend_build.is_file():
    unused_backend_build.unlink()

forge = file("forge/build.gradle")
text = forge.read_text(encoding="utf-8")
marker = "// HariMultiThread Ultimate isolated backend packaging"
if marker in text:
    raise SystemExit("backend packaging block already present unexpectedly")
text += '''

// HariMultiThread Ultimate isolated backend packaging
// A dedicated pure-Java source set compiles the child-loaded backend against
// the tiny common-side API + LWJGL. It is NOT a Forge mod/source set and is never
// placed on ModLauncher's module path.
sourceSets {
    harimtVulkanBackend {
        java.srcDir file('../vulkan-backend/src/main/java')
    }
}

dependencies {
    harimtVulkanBackendCompileOnly project(':common')
    harimtVulkanBackendImplementation 'org.lwjgl:lwjgl:3.3.1'
    harimtVulkanBackendImplementation 'org.lwjgl:lwjgl-vulkan:3.3.1'
}

tasks.named('compileHarimtVulkanBackendJava') {
    options.encoding = 'UTF-8'
    options.release = 17
}

def harimtVulkanBackendJar = tasks.register('harimtVulkanBackendJar', Jar) {
    archiveBaseName.set('harimt-vulkan-backend')
    archiveVersion.set('3.3.1')
    from sourceSets.harimtVulkanBackend.output
    manifest {
        attributes(
                'Implementation-Title': 'HariMultiThread Ultimate Vulkan Backend',
                'Implementation-Version': '3.3.1'
        )
    }
}

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

# Tighten the isolated backend against LWJGL 3.3.1's generated API. These edits
# are intentionally performed after overlay copying so they cannot be masked by
# an older source snapshot in the release overlay.
backend = file("vulkan-backend/src/main/java/com/axalotl/async/vulkanruntime/LwjglVulkanBackend.java")
backend_text = backend.read_text(encoding="utf-8")

def backend_replace(old, new, count=1):
    global backend_text
    found = backend_text.count(old)
    if found != count:
        raise SystemExit(f"backend source drift: expected {count}, found {found}: {old[:150]!r}")
    backend_text = backend_text.replace(old, new, count)

backend_replace(
'''            // VK auto-loads the system Vulkan loader unless explicit-init was requested.
            try {
                VK.getFunctionProvider();
            } catch (IllegalStateException notInitialized) {
                VK.create();
            }
''',
'''            // VK.getFunctionProvider() may be null after VK.destroy(), while
            // explicit-init configurations can throw before create(). Handle both
            // so integrated-server stop/start recreates the Vulkan loader cleanly.
            try {
                if (VK.getFunctionProvider() == null) VK.create();
            } catch (IllegalStateException notInitialized) {
                VK.create();
            }
''')
backend_replace(".apiVersion(VK_API_VERSION_1_1);", ".apiVersion(VK_API_VERSION_1_0);")
backend_replace(
'''            vkCmdDispatch(commandBuffer, (count + 63) / 64, 1, 1);
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
''',
'''            vkCmdDispatch(commandBuffer, (count + 63) / 64, 1, 1);

            // The fence synchronizes command completion, but device shader writes
            // still need to be made available to the host memory domain before the
            // mapped pair counter/output arrays are read. HOST_COHERENT removes
            // flush/invalidate requirements; it does not replace this device->host
            // availability dependency.
            VkMemoryBarrier.Buffer hostReadBarrier = VkMemoryBarrier.calloc(1, stack);
            hostReadBarrier.get(0)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_HOST_READ_BIT);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_HOST_BIT,
                    0,
                    hostReadBarrier,
                    null,
                    null);
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
''')
backend_replace(
'''            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            check(vkQueueSubmit(queue, submit, fence), "vkQueueSubmit");
''',
'''            VkSubmitInfo.Buffer submits = VkSubmitInfo.calloc(1, stack);
            submits.get(0)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            check(vkQueueSubmit(queue, submits, fence), "vkQueueSubmit");
''')
backend_replace(
'''                writes.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSet)
                        .dstBinding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(i).address(), 1));
''',
'''                writes.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSet)
                        .dstBinding(i)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(i).address(), 1));
''')
backend.write_text(backend_text, encoding="utf-8")

# Fail closed if any parent source still references the deleted reflection stack.
for p in (root / "common/src/main/java").rglob("*.java"):
    text = p.read_text(encoding="utf-8")
    for forbidden in ("VkDeviceManager", "VkBufferManager", "VkComputePipeline"):
        if forbidden in text:
            raise SystemExit(f"legacy Vulkan reference survived in {p.relative_to(root)}: {forbidden}")

print("Compile-checked isolated Vulkan backend architecture applied successfully")
