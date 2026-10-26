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
    private record Key(NativeUiProgram program, VertexFormat format, List<Integer> textureUnits,NativeVertexLayout layout) { Key(NativeUiProgram p,VertexFormat f,List<Integer> u){this(p,f,u,null);} }
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
            if(key.layout!=null)builder.setNativeVertexLayout(key.layout);
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
    /** Direct known-layout consumption of actual native storage, without a CPU upload copy.
     * VAO discovery, instancing, SSBO descriptors and complete Physics draws are separate. */
    public static void drawNativeStorage(VertexFormat format, VertexFormat.Mode mode,
                                         int vertexName,long vertexOffset,int vertices,
                                         int indexName,long indexOffset,int indices,int indexType,int baseVertex) {
        RenderSystem.assertOnRenderThread();NativeUiProgram program=GlProgram.currentNativeProgram();
        Renderer renderer=Renderer.getInstance();
        if(program==null || renderer==null || !Renderer.isRecording() || renderer.getBoundRenderPass()==null)
            throw new IllegalStateException("Direct native storage needs linked program and owned recording pass");
        if(vertices<0 || indices<0)throw new IllegalArgumentException("Negative native draw count");
        if(mode!=VertexFormat.Mode.TRIANGLES && mode!=VertexFormat.Mode.TRIANGLE_STRIP && mode!=VertexFormat.Mode.LINES && mode!=VertexFormat.Mode.LINE_STRIP)
            throw new UnsupportedOperationException("Direct native topology requires an explicit indexed conversion");
        if(vertices==0 || indexName!=0&&indices==0)return;
        validateLayout(program,format);installRetirer();
        int width=indexName==0?0:switch(indexType){case 0x1403->2;case 0x1405->4;default->throw new UnsupportedOperationException("Direct native index type requires device feature wiring");};
        if(vertexOffset<0 || indexOffset<0 || vertexOffset%4!=0 || width!=0&&indexOffset%width!=0)
            throw new IllegalArgumentException("Native vertex/index offset alignment");
        var vertex=NativeBufferObjectApi.drawStorage(vertexName,vertexOffset,(long)vertices*format.getVertexSize(),VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        var index=indexName==0?null:NativeBufferObjectApi.drawStorage(indexName,indexOffset,(long)indices*width,VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        List<Integer> units=new ArrayList<>();for(var sampler:program.samplers()){
            int[] value=GlProgram.getIntUniform(sampler.name());int unit=value==null?0:value[0];
            if(unit<0 || unit>=VTextureSelector.SIZE || VTextureSelector.getImage(unit)==null)throw new IllegalStateException("Original native texture unbound: "+sampler.name());units.add(unit);
        }
        Entry entry=ENTRIES.computeIfAbsent(new Key(program,format,List.copyOf(units)),Entry::new);entry.update(program);
        VRenderSystem.setPrimitiveTopologyGL(mode.asGLMode);renderer.bindGraphicsPipeline(entry.pipeline);renderer.uploadAndBindUBOs(entry.pipeline);
        try(org.lwjgl.system.MemoryStack stack=org.lwjgl.system.MemoryStack.stackPush()){
            var command=Renderer.getCommandBuffer();vkCmdBindVertexBuffers(command,0,stack.longs(vertex.buffer),stack.longs(vertexOffset));
            if(index!=null){vkCmdBindIndexBuffer(command,index.buffer,indexOffset,width==2?VK_INDEX_TYPE_UINT16:VK_INDEX_TYPE_UINT32);vkCmdDrawIndexed(command,indices,1,0,baseVertex,0);}
            else vkCmdDraw(command,vertices,1,0,0);
        }
        draws++;
    }
    static void drawNativeVertexLayout(NativeVertexLayout layout,Map<Integer,NativePixelBufferApi.Store> stores,Map<Integer,Long> offsets,int mode,int first,int count,int instances){drawNativeInput(layout,stores,offsets,mode,first,count,instances,null,0,0);}
    static void drawNativeIndexedVertexLayout(NativeVertexLayout layout,Map<Integer,NativePixelBufferApi.Store> stores,Map<Integer,Long> offsets,int mode,int count,int instances,NativePixelBufferApi.Store index,long offset,int width){drawNativeInput(layout,stores,offsets,mode,0,count,instances,index,offset,width);}
    private static void drawNativeInput(NativeVertexLayout layout,Map<Integer,NativePixelBufferApi.Store> stores,Map<Integer,Long> offsets,int mode,int first,int count,int instances,NativePixelBufferApi.Store index,long indexOffset,int width){
        RenderSystem.assertOnRenderThread();var program=GlProgram.currentNativeProgram();var renderer=Renderer.getInstance();
        if(program==null||renderer==null||!Renderer.isRecording()||renderer.getBoundRenderPass()==null)throw new IllegalStateException("Native custom input needs linked program and actual recording pass");
        if(mode!=4&&mode!=5&&mode!=1&&mode!=3)throw new UnsupportedOperationException("Native topology conversion remains separate");layout.shader(program);installRetirer();
        List<Integer> units=new ArrayList<>();for(var sampler:program.samplers()){int[] v=GlProgram.getIntUniform(sampler.name());int unit=v==null?0:v[0];if(unit<0||unit>=VTextureSelector.SIZE||VTextureSelector.getImage(unit)==null)throw new IllegalStateException("Native custom texture missing");units.add(unit);}
        Entry entry=ENTRIES.computeIfAbsent(new Key(program,null,List.copyOf(units),layout),Entry::new);entry.update(program);VRenderSystem.setPrimitiveTopologyGL(mode);renderer.bindGraphicsPipeline(entry.pipeline);renderer.uploadAndBindUBOs(entry.pipeline);
        try(org.lwjgl.system.MemoryStack stack=org.lwjgl.system.MemoryStack.stackPush()){var command=Renderer.getCommandBuffer();for(var binding:layout.bindings())vkCmdBindVertexBuffers(command,binding.index(),stack.longs(stores.get(binding.index()).buffer),stack.longs(offsets.get(binding.index())));if(index==null)vkCmdDraw(command,count,instances,first,0);else{vkCmdBindIndexBuffer(command,index.buffer,indexOffset,width==2?VK_INDEX_TYPE_UINT16:VK_INDEX_TYPE_UINT32);vkCmdDrawIndexed(command,count,instances,0,0,0);}}draws++;
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
                default -> (attribute.type().equals("float") || attribute.type().equals("vec2") || attribute.type().equals("vec3") || attribute.type().equals("vec4"))
                        && (e.getType()==VertexFormatElement.Type.FLOAT && e.getCount()==(attribute.type().equals("float")?1:attribute.type().charAt(3)-'0')
                        || attribute.type().equals("vec4") && e.getUsage()==VertexFormatElement.Usage.COLOR && e.getType()==VertexFormatElement.Type.UBYTE && e.getCount()==4);
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
