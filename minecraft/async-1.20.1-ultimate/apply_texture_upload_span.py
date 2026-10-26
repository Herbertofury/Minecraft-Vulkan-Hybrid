#!/usr/bin/env python3
"""Apply an exact bounded upload after the frozen 2.4.9 reconstruction recipe."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).parent

def edit(relative, old, new):
    path = root / relative
    source = path.read_text()
    assert source.count(old) == 1, (relative, old)
    path.write_text(source.replace(old, new))

edit('forge/src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java',
     '        long imageSize = buffer.limit();',
     '''        if (width == 0 || height == 0) return;
        int rowLength = TextureUploadSpan.rowLength(width, unpackRowLength);
        int sourceOffset = TextureUploadSpan.sourceOffset(rowLength, unpackSkipRows, unpackSkipPixels, this.formatSize);
        int imageSize = TextureUploadSpan.byteSize(width, height, rowLength, this.formatSize);
        if (sourceOffset > buffer.remaining() - imageSize)
            throw new IllegalArgumentException("Texture upload rectangle exceeds source buffer");''')
edit('forge/src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java',
     '        stagingBuffer.copyBuffer((int) imageSize, buffer);',
     '        stagingBuffer.copyBufferRange(imageSize, buffer, sourceOffset);')
edit('forge/src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java',
     '(int) (stagingBuffer.getOffset() + (unpackRowLength * unpackSkipRows + unpackSkipPixels) * this.formatSize), unpackRowLength, height);',
     '(int) stagingBuffer.getOffset(), rowLength, height);')
edit('forge/src/main/java/net/vulkanmod/vulkan/memory/StagingBuffer.java',
     '    public void copyBuffer(int size, ByteBuffer byteBuffer) {',
     '''    public void copyBuffer(int size, ByteBuffer byteBuffer) {
        copyBufferRange(size, byteBuffer, 0);
    }

    /** Copy exactly the required source span without changing the caller's buffer cursor. */
    public void copyBufferRange(int size, ByteBuffer byteBuffer, int sourceOffset) {
        if (size < 0 || sourceOffset < 0 || sourceOffset > byteBuffer.remaining() - size)
            throw new IllegalArgumentException("Staging copy exceeds source buffer");''')
edit('forge/src/main/java/net/vulkanmod/vulkan/memory/StagingBuffer.java',
     'MemoryUtil.memAddress(byteBuffer), size);',
     'MemoryUtil.memAddress(byteBuffer) + sourceOffset, size);')
for path in (here / 'fps-src').rglob('*'):
    if path.is_file():
        target = root / path.relative_to(here / 'fps-src')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
edit('gradle.properties', 'version=2.4.9-noxviola.1-vulkan-hybrid',
     'version=2.4.10-noxviola.1-vulkan-hybrid')
print('Exact texture source spans applied; all pixel rows, upload calls and barriers retained')
