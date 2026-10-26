"""Production package relocation; preserve all canonical helper implementation bytes."""
from pathlib import Path
R=Path(__file__).resolve().parents[3]
def relocated_uniform_source(directory):
    original=(R/'compat/vulkan-shaders/uniforms/src/mvhshadercompat/SpirvUniformBlock.java').read_text(encoding='utf8')
    assert original.count('package mvhshadercompat;')==1
    p=directory/'net/vulkanmod/vulkan/shader/SpirvUniformBlock.java'
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(original.replace('package mvhshadercompat;','package net.vulkanmod.vulkan.shader;'),encoding='utf8')
    assert p.read_text().replace('package net.vulkanmod.vulkan.shader;','package mvhshadercompat;')==original
    return p
