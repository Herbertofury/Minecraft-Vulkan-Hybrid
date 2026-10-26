#!/usr/bin/env python3
"""Repair the actual renderer's acquire wait and presentation semaphore ownership."""
from pathlib import Path

def apply(root: Path):
    path = root / 'forge/src/main/java/net/vulkanmod/vulkan/Renderer.java'
    text = path.read_text()
    def replace(old, new, count=1):
        nonlocal text
        if text.count(old) != count:
            raise SystemExit('swapchain source drift: ' + old[:100])
        text = text.replace(old, new)
    replace('    private boolean recordingCmds = false;',
            '    private boolean recordingCmds = false;\n    private boolean swapChainAcquirePending;')
    replace('renderFinishedSemaphores = new ArrayList<>(framesNum);',
            'renderFinishedSemaphores = new ArrayList<>(imagesNum);\n        swapChainAcquirePending = false;')
    replace('''                        || vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS
''', '')
    replace('''                renderFinishedSemaphores.add(pRenderFinishedSemaphore.get(0));
''', '')
    replace('''                inFlightFences.add(pFence.get(0));

            }
''', '''                inFlightFences.add(pFence.get(0));

            }
            // A frame fence does not prove presentation has consumed its wait.
            // Reacquiring this image does: keep one present semaphore per image.
            for (int i = 0; i < imagesNum; i++) {
                if (vkCreateSemaphore(device, semaphoreInfo, null, pRenderFinishedSemaphore) != VK_SUCCESS)
                    throw new RuntimeException("Failed to create presentation semaphore for image: " + i);
                renderFinishedSemaphores.add(pRenderFinishedSemaphore.get(0));
            }
''')
    replace('''            imageIndex = pImageIndex.get(0);
''', '''            imageIndex = pImageIndex.get(0);
            swapChainAcquirePending = true;
''')
    replace('''            submitInfo.waitSemaphoreCount(1);
            submitInfo.pWaitSemaphores(stack.longs(imageAvailableSemaphores.get(currentFrame)));
            submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
''', '''            prepareSwapChainSubmit(submitInfo, stack);
''')
    replace('renderFinishedSemaphores.get(currentFrame)', 'renderFinishedSemaphores.get(imageIndex)', 2)
    # All three command-buffer submissions may be the first one touching the
    # acquired image. The screenshot/loading/readback paths must wait as well.
    replace('''            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(stack.pointers(currentCmdBuffer));''',
            '''            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            prepareSwapChainSubmit(submitInfo, stack);
            submitInfo.pCommandBuffers(stack.pointers(currentCmdBuffer));''')
    replace('''            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);

            submitInfo.pCommandBuffers(stack.pointers(currentCmdBuffer));''',
            '''            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            prepareSwapChainSubmit(submitInfo, stack);

            submitInfo.pCommandBuffers(stack.pointers(currentCmdBuffer));''')
    replace('''            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);''',
            '''            swapChainAcquirePending = false;

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);''')
    replace('''            vkResult = vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);''',
            '''            swapChainAcquirePending = false;
            vkResult = vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);''')
    replace('''            vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);

            vkResetCommandBuffer(currentCmdBuffer, 0);''',
            '''            swapChainAcquirePending = false;
            vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);

            vkResetCommandBuffer(currentCmdBuffer, 0);''')
    start = text.index('    void waitForSwapChain() {')
    end = text.index('    @SuppressWarnings("UnreachableCode")', start)
    text = text[:start] + '''    /** The first submission, including an early flush, owns the acquire wait. */
    private void prepareSwapChainSubmit(VkSubmitInfo info, MemoryStack stack) {
        if (swapChainAcquirePending) {
            info.waitSemaphoreCount(1);
            info.pWaitSemaphores(stack.longs(imageAvailableSemaphores.get(currentFrame)));
            // The command buffer may begin with layout transitions or transfers,
            // before color attachment output. No extra host fence is introduced.
            info.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
        }
    }

    void waitForSwapChain() {
        if (!swapChainAcquirePending) return;
        vkResetFences(device, inFlightFences.get(currentFrame));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo info = VkSubmitInfo.calloc(stack).sType$Default();
            prepareSwapChainSubmit(info, stack);
            int result = vkQueueSubmit(DeviceManager.getGraphicsQueue().queue(), info, inFlightFences.get(currentFrame));
            if (result != VK_SUCCESS)
                throw new RuntimeException("Failed to wait for acquired swapchain image: " + VkResult.decode(result));
            swapChainAcquirePending = false;
            result = vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);
            if (result != VK_SUCCESS)
                throw new RuntimeException("Failed acquired image wait fence: " + VkResult.decode(result));
        }
    }

''' + text[end:]
    replace('''            vkDestroySemaphore(device, renderFinishedSemaphores.get(i), null);
        }
    }''', '''        }
        for (long semaphore : renderFinishedSemaphores)
            vkDestroySemaphore(device, semaphore, null);
        swapChainAcquirePending = false;
    }''')
    path.write_text(text)

if __name__ == '__main__':
    import sys
    apply(Path(sys.argv[1]).resolve())
