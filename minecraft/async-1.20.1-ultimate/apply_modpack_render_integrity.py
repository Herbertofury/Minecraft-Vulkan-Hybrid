#!/usr/bin/env python3
"""Keep framebuffer readback and dynamic state valid across screenshot restarts."""
from pathlib import Path
import json

def apply(root: Path):
    def edit(name, old, new):
        p = root / name
        text = p.read_text()
        if text.count(old) != 1:
            raise SystemExit('modpack render source drift: ' + name + ': ' + old[:100])
        p.write_text(text.replace(old, new))
    edit('forge/src/main/java/net/vulkanmod/vulkan/framebuffer/Framebuffer.java',
         '.setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)',
         '.setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)')
    edit('forge/src/main/java/net/vulkanmod/vulkan/framebuffer/SwapChain.java',
         '            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);',
         '''            int requiredImageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
            if ((surfaceProperties.capabilities.supportedUsageFlags() & requiredImageUsage) != requiredImageUsage)
                throw new IllegalStateException("Vulkan surface does not support render-target sampling and readback: "
                        + surfaceProperties.capabilities.supportedUsageFlags());
            createInfo.imageUsage(requiredImageUsage);''')
    name = 'forge/src/main/java/net/vulkanmod/vulkan/Renderer.java'
    edit(name, '    private boolean swapChainAcquirePending;',
         '    private boolean swapChainAcquirePending;\n    private float depthBiasUnits, depthBiasFactor;')
    edit(name, '            this.recordingCmds = true;\n            resumeFramebuffer.beginRenderPass',
         '            this.recordingCmds = true;\n            resetDynamicState(currentCmdBuffer);\n            resumeFramebuffer.beginRenderPass')
    edit(name, '        vkCmdSetDepthBias(commandBuffer, 0.0F, 0.0F, 0.0F);',
         '        vkCmdSetDepthBias(commandBuffer, INSTANCE.depthBiasUnits, 0.0F, INSTANCE.depthBiasFactor);')
    edit(name, '        vkCmdSetDepthBias(commandBuffer, units, 0.0f, factor);',
         '        INSTANCE.depthBiasUnits = units;\n        INSTANCE.depthBiasFactor = factor;\n        if (INSTANCE.recordingCmds) vkCmdSetDepthBias(commandBuffer, units, 0.0f, factor);')

    # Each height has its own cache. Arrays.fill(new Layer()) aliased all sixteen
    # heights, causing one biome layer's colors to leak into another.
    name = 'forge/src/main/java/net/vulkanmod/render/chunk/build/TintCache.java'
    edit(name, '        Arrays.fill(layers, new Layer());',
         '        for (int y = 0; y < layers.length; y++) layers[y] = new Layer();')
    edit(name, '        this.blendRadius = Minecraft.getInstance().options.biomeBlendRadius().get();',
         '        this.blendRadius = blendRadius;')
    edit(name, '        int[] values = layer.getValues(colorResolver);', '''        int[] values;
        if (colorResolver == BiomeColors.GRASS_COLOR_RESOLVER
                || colorResolver == BiomeColors.FOLIAGE_COLOR_RESOLVER
                || colorResolver == BiomeColors.WATER_COLOR_RESOLVER) {
            values = layer.getValues(colorResolver);
        } else {
            // Preserve the original mod resolver, including coordinate-dependent
            // colors. This cache belongs to this section build and height only.
            values = layer.custom.computeIfAbsent(colorResolver, resolver -> {
                int[] colors = new int[dataSize];
                BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
                Level level = WorldRenderer.getLevel();
                int absY = (secY << 4) + relY;
                for (int z = minZ; z < maxZ; z++) for (int x = minX; x < maxX; x++) {
                    Biome biome = level.getBiome(sample.set(x, absY, z)).value();
                    colors[(x - minX) + (z - minZ) * totalWidth] = resolver.getColor(biome, x, z);
                }
                if (blendRadius > 0) com.axalotl.async.forge.client.ModdedBiomeTint.blur(colors, totalWidth, blendRadius);
                return colors;
            });
        }''')
    edit(name, '        private boolean invalidated = true;',
         '        private boolean invalidated = true;\n        private final java.util.IdentityHashMap<ColorResolver, int[]> custom = new java.util.IdentityHashMap<>();')
    edit(name, '            this.invalidated = true;',
         '            this.invalidated = true;\n            this.custom.clear();')

    # Optional old Rubidium builds reject every custom ColorResolver. Keep their
    # three vanilla fast paths and blend mod colors from their original snapshot.
    config = root / 'forge/src/main/resources/harimt.forge.mixins.json'
    data = json.loads(config.read_text())
    assert 'client.rubidium.RubidiumWorldSliceMixin' not in data['client']
    data['client'].append('client.rubidium.RubidiumWorldSliceMixin')
    data['plugin'] = 'com.axalotl.async.forge.mixin.HariForgeMixinPlugin'
    config.write_text(json.dumps(data, indent=2) + '\n')
    edit('forge/src/main/java/com/axalotl/async/forge/mixin/HariForgeMixinPlugin.java',
         '        if(!mixinClassName.contains(".client.embeddium.")) return true;', '''        if(mixinClassName.contains(".client.rubidium.")) {
            var list = FMLLoader.getLoadingModList();
            return list.getModFileById("rubidium") != null && list.getModFileById("embeddium") == null;
        }
        if(!mixinClassName.contains(".client.embeddium.")) return true;''')

if __name__ == '__main__':
    import sys
    apply(Path(sys.argv[1]).resolve())
