package net.vulkanmod.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.*;
import net.vulkanmod.vulkan.shader.descriptor.*;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Original native bridge for legacy UI programs using Minecraft's actual buffered draws. */
public final class NativeUiRenderBridge {
    private record Key(NativeUiProgram program, VertexFormat format, List<Integer> textureUnits) {}
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();
    private static boolean installed;
    private static long draws;
    private static final class Entry {
        final GraphicsPipeline pipeline;
        final ManualUBO uniform;
        // ManualUBO's native source pointer must stay alive until the next synchronous upload.
        ByteBuffer source;
        Entry(Key key) {
            NativeUiProgram p = key.program;
            uniform = p.uniformBytes() == 0 ? null : new ManualUBO(0, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, p.uniformBytes() / 4);
            List<UBO> blocks = new ArrayList<>(); if (uniform != null) blocks.add(uniform);
            List<ImageDescriptor> samplers = new ArrayList<>();
            for (int i = 0; i < p.samplers().size(); i++) { var s = p.samplers().get(i); samplers.add(new ImageDescriptor(s.binding(), "sampler2D", s.name(), key.textureUnits.get(i))); }
            Pipeline.Builder builder = new Pipeline.Builder(key.format, "mvh-native-legacy-ui");
            builder.setUniforms(blocks, samplers);
            builder.setSPIRVs(new SPIRVUtils.SPIRV(0, p.vertexSpirv()), new SPIRVUtils.SPIRV(0, p.fragmentSpirv()));
            pipeline = builder.createGraphicsPipeline();
        }
        void update(NativeUiProgram program) {
            source = GlProgram.currentNativeUniforms();
            if (program.uniformNames().contains("mvh_ModelViewProjection")) {
                float[] matrix = new float[16];
                new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).get(matrix);
                program.writeFloats(source, "mvh_ModelViewProjection", matrix);
            }
            if (uniform != null) uniform.setSrc(MemoryUtil.memAddress(source), source.remaining());
        }
    }
    public static long nativeDraws() { return draws; }
    public static void validateProgram(int handle) {
        RenderSystem.assertOnRenderThread(); NativeUiProgram p = GlProgram.nativeProgram(handle);
        if (p == null) { GlProgram.nativeValidationResult(handle, false, "No linked native executable"); return; }
        try {
            Renderer renderer = Renderer.getInstance();
            if (renderer == null || renderer.getBoundRenderPass() == null) throw new IllegalStateException("Native UI validation needs the actual target render pass");
            VertexFormat format = p.attributes().stream().anyMatch(a -> a.name().equals("mvh_TexCoord")) ? DefaultVertexFormat.POSITION_TEX : DefaultVertexFormat.POSITION_COLOR;
            validateLayout(p, format);
            List<Integer> units = p.samplers().stream().map(ignored -> 0).toList();
            Entry entry = ENTRIES.computeIfAbsent(new Key(p, format, units), Entry::new);
            // Force real Vulkan pipeline creation, not only lazy metadata construction.
            entry.pipeline.getHandle(PipelineState.getCurrentPipelineState(renderer.getBoundRenderPass()));
            installRetirer();GlProgram.nativeValidationResult(handle, true, "");
        } catch (RuntimeException error) { GlProgram.nativeValidationResult(handle, false, error.getMessage()); }
    }
    private static void installRetirer() { if (!installed) { GlProgram.setNativeProgramRetirer(NativeUiRenderBridge::retire); installed = true; } }
    /** Consumes/releases only when an actual linked custom program is current. */
    public static boolean draw(BufferBuilder.RenderedBuffer buffer) {
        NativeUiProgram program = GlProgram.currentNativeProgram(); if (program == null) return false;
        RenderSystem.assertOnRenderThread();
        installRetirer();
        try {
            Renderer renderer = Renderer.getInstance(); if (renderer == null || !Renderer.isRecording()) throw new IllegalStateException("Native UI draw needs an actual recording frame");
            var state = buffer.drawState(); if (state.vertexCount() == 0) return true;
            validateLayout(program, state.format());
            List<Integer> units = new ArrayList<>();
            for (var sampler : program.samplers()) {
                int[] uniform = GlProgram.getIntUniform(sampler.name()); int unit = uniform == null ? 0 : uniform[0];
                if (unit < 0 || unit >= VTextureSelector.SIZE || VTextureSelector.getImage(unit) == null)
                    throw new IllegalStateException("Original native UI texture is not bound: " + sampler.name());
                units.add(unit);
            }
            Key key = new Key(program, state.format(), List.copyOf(units)); Entry entry = ENTRIES.computeIfAbsent(key, Entry::new); entry.update(program);
            VRenderSystem.setPrimitiveTopologyGL(state.mode().asGLMode);
            renderer.bindGraphicsPipeline(entry.pipeline); renderer.uploadAndBindUBOs(entry.pipeline);
            Renderer.getDrawer().draw(buffer.vertexBuffer(), state.mode(), state.format(), state.vertexCount()); draws++;
            // The ordinary buffered renderer retains its own uploads behind its frame fence.
            return true;
        } finally { buffer.release(); }
    }
    private static void validateLayout(NativeUiProgram p, VertexFormat format) {
        List<VertexFormatElement> elements = format.getElements();
        for (var attribute : p.attributes()) {
            if (attribute.location() >= elements.size()) throw new IllegalArgumentException("Native UI vertex attribute absent");
            VertexFormatElement e = elements.get(attribute.location());
            boolean matches = switch (attribute.name()) {
                case "mvh_Vertex" -> e.getUsage() == VertexFormatElement.Usage.POSITION && e.getType() == VertexFormatElement.Type.FLOAT && e.getCount() == 3;
                case "mvh_Color" -> e.getUsage() == VertexFormatElement.Usage.COLOR && e.getType() == VertexFormatElement.Type.UBYTE && e.getCount() == 4;
                case "mvh_TexCoord" -> e.getUsage() == VertexFormatElement.Usage.UV && e.getType() == VertexFormatElement.Type.FLOAT && e.getCount() == 2;
                default -> false;
            };
            if (!matches) throw new IllegalArgumentException("Native UI vertex layout mismatch: " + attribute.name());
        }
    }
    private static void retire(NativeUiProgram program) {
        RenderSystem.assertOnRenderThread();
        for (var i = ENTRIES.entrySet().iterator(); i.hasNext();) {
            var entry = i.next(); if (entry.getKey().program == program) { entry.getValue().pipeline.scheduleCleanUp(); i.remove(); }
        }
    }
}
