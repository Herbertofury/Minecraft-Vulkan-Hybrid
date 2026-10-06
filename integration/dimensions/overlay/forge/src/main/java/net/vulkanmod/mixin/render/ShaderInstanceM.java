package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.ShaderResources;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.parser.GlslConverter;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(value = ShaderInstance.class, priority = 900)
public class ShaderInstanceM implements ShaderMixed {

    @Shadow(remap = false) @Final private Map<String, com.mojang.blaze3d.shaders.Uniform> f_173333_;
    @Shadow(remap = false) @Final private String f_173300_;

    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173308_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173309_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_200956_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173312_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173318_;

    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_267422_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173315_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173316_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173317_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_202432_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173310_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173319_;
    @Shadow(remap = false) @Final @Nullable public com.mojang.blaze3d.shaders.Uniform f_173311_;


    private GraphicsPipeline pipeline;
    private boolean harimt$closed;
    boolean isLegacy = false;


    @Override
    public boolean usesConvertedShader() { return this.isLegacy; }

    public GraphicsPipeline getPipeline() {
        return pipeline;
    }

    public GraphicsPipeline getPipeline(VertexFormat drawFormat) {
        return pipeline;
    }

    // Target Forge's ResourceLocation constructor: mod shaders registered via RegisterShadersEvent
    // are constructed through it DIRECTLY, while the vanilla String constructor merely delegates to
    // it. Injecting here covers both paths exactly once — an inject on the String overload never
    // fires for mod shaders, leaving them with no pipeline (invisible geometry, e.g. the Wither
    // Storm body).
    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V", at = @At("RETURN"))
    private void create(ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat format, CallbackInfo ci) {
        String name = this.f_173300_;

        try {
            String path = getVulkanShaderPath(name);
            Initializer.LOGGER.info("[VM-DBG] create shader '{}' vulkanPath={}", name, path);
            if (path == null) {
                createLegacyShader(resourceProvider, shaderLocation, format);
                return;
            }

            Pipeline.Builder pipelineBuilder = new Pipeline.Builder(format, path);
            pipelineBuilder.parseBindingsJSON();
            pipelineBuilder.compileShaders();
            this.pipeline = pipelineBuilder.createGraphicsPipeline();
            wireUnresolvedUniforms();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create Vulkan shader " + shaderLocation, e);
        }
    }

    // Mod-owned uniforms must retain their real values; a missing binding is a shader defect.
    private void wireUnresolvedUniforms() {
        if (this.pipeline == null) {
            return;
        }

        for (UBO ubo : this.pipeline.getBuffers()) {
            for (Uniform vUniform : ubo.getUniforms()) {
                if (vUniform.hasSupplier()) {
                    continue;
                }

                com.mojang.blaze3d.shaders.Uniform uniform = this.f_173333_.get(vUniform.getName());

                ByteBuffer byteBuffer;
                if (uniform != null && uniform.getType() <= 3) {
                    byteBuffer = MemoryUtil.memByteBuffer(uniform.getIntBuffer());
                } else if (uniform != null && uniform.getType() <= 10) {
                    byteBuffer = MemoryUtil.memByteBuffer(uniform.getFloatBuffer());
                } else {
                    throw new IllegalStateException("No supplier or ShaderInstance uniform for "
                            + vUniform.getName() + " in shader " + this.f_173300_);
                }

                MappedBuffer mappedBuffer = MappedBuffer.createFromBuffer(byteBuffer);
                vUniform.setSupplier(() -> mappedBuffer);
            }
        }
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;m_173340_(Lnet/minecraft/server/packs/resources/ResourceProvider;Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;)Lcom/mojang/blaze3d/shaders/Program;", remap = false), require = 0)
    private Program loadNames(ResourceProvider resourceProvider, Program.Type type, String name) {
        // Program names are resolved from JSON at constructor return, after uniforms exist.
        return null;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;m_166710_(IILjava/lang/CharSequence;)V", remap = false), require = 0)
    private void bindAttr(int program, int index, CharSequence name) {}

    // The methods below replace vanilla behavior with inject-and-cancel instead of @Overwrite so
    // other mods' handlers targeting ShaderInstance still apply without a mixin crash.

    @Inject(method = "close", at = @At("HEAD"), cancellable = true, remap = false)
    private void onClose(CallbackInfo ci) {
        if (!this.harimt$closed) {
            this.harimt$closed = true;
            try {
                if (this.pipeline != null) this.pipeline.cleanUp();
            } finally {
                // UniformM gives every declared uniform a location, so this map owns all
                // parsed uniforms. Free their native buffers even if pipeline cleanup fails.
                this.f_173333_.values().forEach(com.mojang.blaze3d.shaders.Uniform::close);
            }
        }
        ci.cancel();
    }

    // apply
    @Inject(method = "m_173363_", at = @At("HEAD"), cancellable = true, remap = false)
    private void apply(CallbackInfo ci) {
        ci.cancel();

        if (!this.isLegacy)
            return;

        if (this.f_173308_ != null) {
            this.f_173308_.set(RenderSystem.getModelViewMatrix());
        }

        if (this.f_173309_ != null) {
            this.f_173309_.set(RenderSystem.getProjectionMatrix());
        }

        if (this.f_200956_ != null) {
            this.f_200956_.set(RenderSystem.getInverseViewRotationMatrix());
        }

        if (this.f_173312_ != null) {
            this.f_173312_.set(RenderSystem.getShaderColor());
        }

        if (this.f_267422_ != null) {
            this.f_267422_.set(RenderSystem.getShaderGlintAlpha());
        }

        if (this.f_173315_ != null) {
            this.f_173315_.set(RenderSystem.getShaderFogStart());
        }

        if (this.f_173316_ != null) {
            this.f_173316_.set(RenderSystem.getShaderFogEnd());
        }

        if (this.f_173317_ != null) {
            this.f_173317_.set(RenderSystem.getShaderFogColor());
        }

        if (this.f_202432_ != null) {
            this.f_202432_.set(RenderSystem.getShaderFogShape().getIndex());
        }

        if (this.f_173310_ != null) {
            this.f_173310_.set(RenderSystem.getTextureMatrix());
        }

        if (this.f_173319_ != null) {
            this.f_173319_.set(RenderSystem.getShaderGameTime());
        }

        if (this.f_173311_ != null) {
            Window window = Minecraft.getInstance().getWindow();
            this.f_173311_.set((float)window.getWidth(), (float)window.getHeight());
        }

        if (this.f_173318_ != null) {
            this.f_173318_.set(RenderSystem.getShaderLineWidth());
        }

        // Vanilla ShaderInstance.apply() calls setupShaderLights to feed LIGHT0/1_DIRECTION; this @Overwrite
        // omitted it. Custom mod shaders that use minecraft_mix_light (e.g. Cracker's Wither Storm body)
        // then get unset (0,0,0) directions -> normalize() NaN -> NaN lighting -> the whole model renders
        // invisible. Setting them fixes any custom shader that relies on the vanilla light uniforms.
        RenderSystem.setupShaderLights((ShaderInstance) (Object) this);
    }

    // clear: descriptor cleanup is handled by the pipeline itself.
    @Inject(method = "m_173362_", at = @At("HEAD"), cancellable = true, remap = false)
    private void clear(CallbackInfo ci) {
        ci.cancel();
    }

    private void setUniformSuppliers(UBO ubo) {

        for(Uniform vUniform : ubo.getUniforms()) {
            com.mojang.blaze3d.shaders.Uniform uniform = this.f_173333_.get(vUniform.getName());

            if(uniform == null) {
                throw new IllegalStateException("Uniform " + vUniform.getName()
                        + " missing from shader " + this.f_173300_);
            }

            Supplier<MappedBuffer> supplier;
            ByteBuffer byteBuffer;

            if (uniform.getType() <= 3) {
                byteBuffer = MemoryUtil.memByteBuffer(uniform.getIntBuffer());
            } else if (uniform.getType() <= 10) {
                byteBuffer = MemoryUtil.memByteBuffer(uniform.getFloatBuffer());
            } else {
                throw new RuntimeException("out of bounds value for uniform " + uniform);
            }


            MappedBuffer mappedBuffer = MappedBuffer.createFromBuffer(byteBuffer);
            supplier = () -> mappedBuffer;

            vUniform.setSupplier(supplier);
        }

    }

    private void createLegacyShader(ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat format) throws java.io.IOException {
            ShaderResources.Programs programs = ShaderResources.resolve(resourceProvider, shaderLocation);
            String vshSrc = ShaderResources.read(resourceProvider, programs.vertex());
            String fshSrc = ShaderResources.read(resourceProvider, programs.fragment());

            GlslConverter converter = new GlslConverter();
            Pipeline.Builder builder = new Pipeline.Builder(format, this.f_173300_);

            converter.setFormat(format);
            converter.process(vshSrc, fshSrc);
            UBO ubo = converter.getUBO();
            this.setUniformSuppliers(ubo);

            builder.setUniforms(Collections.singletonList(ubo), converter.getSamplerList());
            builder.compileShaders(this.f_173300_, converter.getVshConverted(), converter.getFshConverted());

            this.pipeline = builder.createGraphicsPipeline();
            this.isLegacy = true;

            Initializer.LOGGER.info("[VM-DBG] legacy shader '{}' fmt={} samplers={} uboUniforms={}",
                    this.f_173300_, format,
                    converter.getSamplerList(),
                    ubo.getUniforms().stream().map(net.vulkanmod.vulkan.shader.layout.Uniform::getName).toList());

    }

    private static String getVulkanShaderPath(String name) {
        ResourceLocation location = ResourceLocation.tryParse(name);
        if (location == null) {
            return null;
        }

        String path = location.getNamespace().equals("minecraft")
                ? String.format("minecraft/core/%s/%s", location.getPath(), location.getPath())
                : String.format("%s/core/%s/%s", location.getNamespace(), location.getPath(), location.getPath());
        String resourcePath = String.format("/assets/vulkanmod/shaders/%s.json", path);
        try (InputStream resource = Pipeline.class.getResourceAsStream(resourcePath)) {
            return resource != null ? path : null;
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
