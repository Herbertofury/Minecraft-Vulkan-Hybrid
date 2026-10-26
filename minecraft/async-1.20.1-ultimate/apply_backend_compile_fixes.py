#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_backend_compile_fixes.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
path = root / "vulkan-backend/src/main/java/com/axalotl/async/vulkanruntime/LwjglVulkanBackend.java"
if not path.is_file():
    raise SystemExit(f"missing isolated backend source: {path}")

text = path.read_text(encoding="utf-8")


def replace(old: str, new: str, count: int = 1) -> None:
    global text
    found = text.count(old)
    if found != count:
        raise SystemExit(f"backend source drift: expected {count}, found {found}: {old[:160]!r}")
    text = text.replace(old, new, count)

replace(
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

replace(".apiVersion(VK_API_VERSION_1_1);", ".apiVersion(VK_API_VERSION_1_0);")

replace(
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

path.write_text(text, encoding="utf-8")
print("Compile-checked Vulkan backend API tightenings applied successfully")
