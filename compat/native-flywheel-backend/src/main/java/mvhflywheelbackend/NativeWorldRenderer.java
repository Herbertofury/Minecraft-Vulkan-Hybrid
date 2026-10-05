package mvhflywheelbackend;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.api.material.*;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.backend.engine.*;
import dev.engine_room.flywheel.backend.engine.uniform.*;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.*;
import net.vulkanmod.vulkan.texture.*;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import java.nio.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Actual native Flywheel model/material/light draws; owned buffers retire behind Hari's frame fences. */
public final class NativeWorldRenderer {
    private static NativeWorldRenderer current;
    private final VkDevice device=Vulkan.getVkDevice();
    private final Thread owner=Thread.currentThread();
    private final Map<Mesh,GpuMesh> meshes=new IdentityHashMap<>();
    private final Map<PipelineKey,Long> pipelines=new HashMap<>();
    private final Map<NativeShaderSources.Code,long[]> modules=new IdentityHashMap<>();
    private final Map<NativeShaderSources.ComputeCode,long[]> computePrograms=new IdentityHashMap<>();
    private final Map<NativeEngine.NativeInstancer<?>,Prepared> prepared=new IdentityHashMap<>();
    private final Map<Integer,Long> samplers=new HashMap<>();
    private final ArrayList<Frame> frames=new ArrayList<>();
    private final long[] layouts=new long[3];
    private long layout,computeLayout,computeStorageLayout,generation=-1;
    private boolean deleted;
    private Frame frame;
    private Slice lightLut,lightSections;
    private final Slice[] uniforms=new Slice[5];
    private record Prepared(Slice data,Slice targets,Slice models,Slice commands,Slice pages,Slice matrices,int submitted,int meshCount) {}
    private record Slice(Buffer buffer,int offset,int bytes) { long pointer(){return MemoryUtil.memAddress(buffer.mapped)+offset;} }
    private record PipelineKey(NativeShaderSources.Code code,long pass,int depthFormat,int cull,boolean offset,
                               DepthTest depth,WriteMask writes,Transparency blend,int stencil,int externalColor) {}
    public NativeWorldRenderer(){
        RenderSystem.assertOnRenderThread();try(MemoryStack s=MemoryStack.stackPush()){
            layouts[0]=descriptorLayout(s,6,VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            layouts[1]=descriptorLayout(s,4,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            layouts[2]=descriptorLayout(s,4,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            LongBuffer out=s.mallocLong(1);check(vkCreatePipelineLayout(device,VkPipelineLayoutCreateInfo.calloc(s).sType$Default().pSetLayouts(s.longs(layouts)),null,out),"pipeline layout");layout=out.get(0);
            computeStorageLayout=descriptorLayout(s,8,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            check(vkCreatePipelineLayout(device,VkPipelineLayoutCreateInfo.calloc(s).sType$Default().pSetLayouts(s.longs(layouts[0],computeStorageLayout)),null,out),"native cull layout");computeLayout=out.get(0);
        }catch(Throwable e){destroy();throw e;}
    }
    private void live(){if(deleted||Thread.currentThread()!=owner)throw new IllegalStateException("Native Flywheel render/lifetime owner violation");}
    public void beginFrame(LightStorage light){
        live();if(!Renderer.isRecording())throw new IllegalStateException("Native Flywheel outside command recording");
        int index=Renderer.getCurrentFrame();while(frames.size()<=index)frames.add(new Frame());frame=frames.get(index);
        frame.begin(Renderer.getMvhRenderEpoch());current=this;
        for(UniformBuffer u:Uniforms.nativeBuffers())bindOriginalUniform(u);
        frame.lightSnapshot(light);
        if(generation!=NativeShaderSources.generation()){retirePrograms();generation=NativeShaderSources.generation();}
    }
    public static void bindOriginalUniform(UniformBuffer u){
        if(current==null)throw new IllegalStateException("Flywheel uniform bind before native frame");current.live();
        Slice slice=current.frame.allocate(u.nativeBytes().remaining());MemoryUtil.memCopy(u.ptr(),slice.pointer(),slice.bytes);current.uniforms[u.index()]=slice;
    }
    public static void invalidateOriginalUniform(UniformBuffer u){
        // Writers retain their CPU allocation, exactly as upstream. Native snapshots are frame-owned.
        if(current!=null){current.live();current.uniforms[u.index()]=null;NativeShaderSources.invalidate();}
    }
    /** Complete original instance/model snapshots before dispatching on the current graphics queue. */
    void prepare(List<NativeEngine.NativeInstancer<?>> requests,List<Integer> selection){
        live();prepared.clear();if(requests.isEmpty())return;
        for(var instances:requests){
            int count=selection==null?instances.count():selection.size();if(count==0)continue;
            int stride=instances.type.layout().byteSize(),pages=(instances.handles.size()+31)/32;
            Slice data=frame.allocate(Math.max(4,Math.multiplyExact(instances.handles.size(),stride))),targets=frame.allocate(Math.max(4,Math.multiplyExact(count,4)));
            Slice model=frame.allocate(28),pageData=frame.allocate(Math.multiplyExact(pages,8)),matrices=frame.allocate(224);
            BitSet selected=null;if(selection!=null){selected=new BitSet();for(int i:selection)selected.set(i);}
            for(int i=0;i<instances.handles.size();i++){var h=instances.handles.get(i);if(h==null||h.deleted)continue;instances.write(i,data.pointer()+i*(long)stride);
                if(h.visible&&(selected==null||selected.get(i))){long ptr=pageData.pointer()+(i/32)*8L+4;MemoryUtil.memPutInt(ptr,MemoryUtil.memGetInt(ptr)|(1<<(i&31)));}}
            var sphere=instances.model.boundingSphere();long p=model.pointer();MemoryUtil.memPutInt(p+8,instances.context.embedded()?1:0);
            MemoryUtil.memPutFloat(p+12,sphere.x());MemoryUtil.memPutFloat(p+16,sphere.y());MemoryUtil.memPutFloat(p+20,sphere.z());MemoryUtil.memPutFloat(p+24,sphere.w());
            Matrix4f pose=new Matrix4f();Matrix3f normal=new Matrix3f();instances.context.compose(pose,normal);
            ByteBuffer matrix=matrices.buffer.mapped.duplicate().order(ByteOrder.LITTLE_ENDIAN);int m=matrices.offset+112;pose.get(m,matrix);
            for(int col=0;col<3;col++)for(int row=0;row<3;row++)matrix.putFloat(m+64+col*16+row*4,normal.get(col,row));
            int meshes=instances.model.meshes().size();if(meshes==0)continue;Slice commands=frame.allocate(Math.multiplyExact(meshes,36));
            for(int i=0;i<meshes;i++)MemoryUtil.memPutInt(commands.pointer()+i*36L,instances.model.meshes().get(i).mesh().indexCount());
            var snapshot=new Prepared(data,targets,model,commands,pageData,matrices,count,meshes);prepared.put(instances,snapshot);frame.feedback.add(snapshot);
        }
        if(prepared.isEmpty())return;
        Renderer.getInstance().runOutsideRenderPass(command->{try(MemoryStack s=MemoryStack.stackPush()){
            var host=VkMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(VK_ACCESS_HOST_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT|VK_ACCESS_UNIFORM_READ_BIT);
            vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,host,null,null);
            for(var instances:requests){var snapshot=prepared.get(instances);if(snapshot==null)continue;
                var code=NativeShaderSources.compute(instances.type);long[] programs=computePrograms.computeIfAbsent(code,this::computePipelines);
                long[] sets=frame.descriptors(new long[]{layouts[0],computeStorageLayout});
                for(int i=0;i<5;i++)writeBuffer(s,sets[0],i,uniforms[i],VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                writeBuffer(s,sets[0],5,frame.allocate(128),VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                Slice[] storage={snapshot.data,snapshot.targets,lightLut,lightSections,snapshot.models,snapshot.commands,snapshot.pages,snapshot.matrices};
                for(int i=0;i<storage.length;i++)writeBuffer(s,sets[1],i,storage[i],VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_COMPUTE,computeLayout,0,s.longs(sets),null);
                vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_COMPUTE,programs[0]);vkCmdDispatch(command,snapshot.pages.bytes/8,1,1);NativeEngine.GPU_CULL_DISPATCHES.incrementAndGet();
                var cull=VkMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,cull,null,null);
                vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_COMPUTE,programs[1]);vkCmdDispatch(command,(snapshot.meshCount+31)/32,1,1);NativeEngine.GPU_APPLY_DISPATCHES.incrementAndGet();
            }
            var ready=VkMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_INDIRECT_COMMAND_READ_BIT|VK_ACCESS_HOST_READ_BIT);
            vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_VERTEX_SHADER_BIT|VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT|VK_PIPELINE_STAGE_HOST_BIT,0,ready,null,null);
        }});
    }
    void render(NativeEngine.Context context,NativeEngine.NativeInstancer<?> instances,List<Integer> selection,int crumble){
        live();int count=selection==null?instances.count():selection.size();if(count==0)return;
        if(selection!=null)prepare(List.of(instances),selection);Prepared snapshot=prepared.get(instances);if(snapshot==null)throw new IllegalStateException("Original visual has no completed native compute snapshot");
        Slice data=snapshot.data,targets=snapshot.targets;int meshIndex=-1;
        for(var part:instances.model.meshes()){
            meshIndex++;
            Mesh mesh=part.mesh();if(mesh.indexCount()==0||mesh.vertexCount()==0)continue;
            Material material=part.material();GpuMesh gpu=meshes.computeIfAbsent(mesh,GpuMesh::new);
            NativeShaderSources.Code code=NativeShaderSources.get(instances.type,material,context.embedded(),crumble>=0);
            if(generation!=code.generation()){retirePrograms();generation=code.generation();}
            var pass=Renderer.getInstance().getBoundRenderPass();if(pass==null)throw new IllegalStateException("Flywheel draw without render pass");
            int stencil=PipelineState.getStencilState();if(VRenderSystem.stencilTest&&!VulkanImage.hasStencilComponent(pass.getFramebuffer().getDepthFormat()))throw new IllegalStateException("Ponder clipping requires a stencil attachment");
            PipelineKey key=new PipelineKey(code,pass.getId(),pass.getFramebuffer().getDepthFormat(),material.backfaceCulling()?VK_CULL_MODE_BACK_BIT:VK_CULL_MODE_NONE,
                material.polygonOffset()||crumble>=0,material.depthTest(),crumble>=0?WriteMask.COLOR:material.writeMask(),crumble>=0?Transparency.CRUMBLING:material.transparency(),stencil,VRenderSystem.colorMask);
            long pipeline=pipelines.computeIfAbsent(key,this::pipeline);
            Slice draw=frame.allocate(128);ByteBuffer b=draw.buffer.mapped.duplicate().order(ByteOrder.LITTLE_ENDIAN);int p=draw.offset;
            b.putInt(p,MaterialEncoder.packUberShader(material)).putInt(p+4,MaterialEncoder.packProperties(material)).putInt(p+8,0);
            Matrix4f pose=new Matrix4f();Matrix3f normal=new Matrix3f();context.compose(pose,normal);pose.get(p+16,b);
            for(int col=0;col<3;col++)for(int row=0;row<3;row++)b.putFloat(p+80+col*16+row*4,normal.get(col,row));
            long[] sets=frame.descriptors();try(MemoryStack s=MemoryStack.stackPush()){
                for(int i=0;i<5;i++){if(uniforms[i]==null)throw new IllegalStateException("Missing original frame uniform "+i);writeBuffer(s,sets[0],i,uniforms[i],VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);}
                writeBuffer(s,sets[0],5,draw,VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                Slice[] ssbos={data,targets,lightLut,lightSections};for(int i=0;i<4;i++)writeBuffer(s,sets[1],i,ssbos[i],VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                Minecraft mc=Minecraft.getInstance();VulkanImage diffuse=((VAbstractTextureI)mc.getTextureManager().getTexture(material.texture())).getVulkanImage();
                VulkanImage overlay=VTextureSelector.getImage(1),light=VTextureSelector.getImage(2);
                VulkanImage breaking=crumble<0?diffuse:((VAbstractTextureI)mc.getTextureManager().getTexture(new ResourceLocation("minecraft","textures/block/destroy_stage_"+crumble+".png"))).getVulkanImage();
                VulkanImage[] textures={diffuse,overlay,light,breaking};
                for(int i=0;i<4;i++){
                    if(textures[i]==null)throw new IllegalStateException("Original Flywheel texture missing at binding "+i);
                    writeImage(s,sets[2],i,textures[i],i==0?sampler(material.blur(),material.mipmap()):sampler(i==2,false));
                }
                VkCommandBuffer command=Renderer.getCommandBuffer();vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
                vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_GRAPHICS,layout,0,s.longs(sets),null);
                vkCmdBindVertexBuffers(command,0,s.longs(gpu.vertices.buffer),s.longs(0));vkCmdBindIndexBuffer(command,gpu.indices.buffer,0,VK_INDEX_TYPE_UINT32);
                vkCmdSetStencilCompareMask(command,VK_STENCIL_FACE_FRONT_AND_BACK,VRenderSystem.stencilFuncMask);
                vkCmdSetStencilWriteMask(command,VK_STENCIL_FACE_FRONT_AND_BACK,VRenderSystem.stencilWriteMask);
                vkCmdSetStencilReference(command,VK_STENCIL_FACE_FRONT_AND_BACK,VRenderSystem.stencilRef);
                vkCmdDrawIndexedIndirect(command,snapshot.commands.buffer.buffer,snapshot.commands.offset+meshIndex*36L,1,36);
                NativeEngine.MODEL_DRAWS.incrementAndGet();if(crumble>=0)NativeEngine.CRUMBLING_DRAWS.incrementAndGet();
            }finally{Renderer.getInstance().invalidateRenderState();}
        }
    }
    private long sampler(boolean blur,boolean mipmap){int key=(blur?1:0)|(mipmap?2:0);return samplers.computeIfAbsent(key,k->{try(MemoryStack s=MemoryStack.stackPush()){
        LongBuffer out=s.mallocLong(1);int filter=blur?VK_FILTER_LINEAR:VK_FILTER_NEAREST;
        check(vkCreateSampler(device,VkSamplerCreateInfo.calloc(s).sType$Default().magFilter(filter).minFilter(filter).mipmapMode(blur?VK_SAMPLER_MIPMAP_MODE_LINEAR:VK_SAMPLER_MIPMAP_MODE_NEAREST)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT).addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT).addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT).minLod(0).maxLod(mipmap?VK_LOD_CLAMP_NONE:0).maxAnisotropy(1),null,out),"material sampler");return out.get(0);}});}
    private long descriptorLayout(MemoryStack s,int count,int type){var bindings=VkDescriptorSetLayoutBinding.calloc(count,s);for(int i=0;i<count;i++)bindings.get(i).binding(i).descriptorType(type).descriptorCount(1).stageFlags(VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT|VK_SHADER_STAGE_COMPUTE_BIT);
        LongBuffer out=s.mallocLong(1);check(vkCreateDescriptorSetLayout(device,VkDescriptorSetLayoutCreateInfo.calloc(s).sType$Default().pBindings(bindings),null,out),"descriptor layout");return out.get(0);}
    private void writeBuffer(MemoryStack s,long set,int binding,Slice slice,int type){var info=VkDescriptorBufferInfo.calloc(1,s).buffer(slice.buffer.buffer).offset(slice.offset).range(slice.bytes);
        vkUpdateDescriptorSets(device,VkWriteDescriptorSet.calloc(1,s).sType$Default().dstSet(set).dstBinding(binding).descriptorCount(1).descriptorType(type).pBufferInfo(info),null);}
    private void writeImage(MemoryStack s,long set,int binding,VulkanImage image,long sampler){var info=VkDescriptorImageInfo.calloc(1,s).imageView(image.getImageView()).sampler(sampler).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        vkUpdateDescriptorSets(device,VkWriteDescriptorSet.calloc(1,s).sType$Default().dstSet(set).dstBinding(binding).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(info),null);}
    private long module(byte[] bytes){ByteBuffer b=MemoryUtil.memAlloc(bytes.length).put(bytes).flip();try(MemoryStack s=MemoryStack.stackPush()){
        LongBuffer out=s.mallocLong(1);check(vkCreateShaderModule(device,VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(b),null,out),"shader module");return out.get(0);
    }finally{MemoryUtil.memFree(b);}}
    private long[] computePipelines(NativeShaderSources.ComputeCode code){long[] result=new long[4];try(MemoryStack s=MemoryStack.stackPush()){
        byte[][] binaries={code.cull(),code.apply()};for(int i=0;i<2;i++){result[2+i]=module(binaries[i]);var info=VkComputePipelineCreateInfo.calloc(1,s).sType$Default().layout(computeLayout);
            info.stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(result[2+i]).pName(s.UTF8("main"));LongBuffer out=s.mallocLong(1);check(vkCreateComputePipelines(device,0,info,null,out),"original native instance cull/apply pipeline");result[i]=out.get(0);}return result;
        }catch(Throwable e){destroyCompute(result);throw e;}}
    private void destroyCompute(long[] programs){for(int i=0;i<2;i++)if(programs[i]!=0)vkDestroyPipeline(device,programs[i],null);for(int i=2;i<4;i++)if(programs[i]!=0)vkDestroyShaderModule(device,programs[i],null);}
    private long pipeline(PipelineKey k){
        if(pipelines.size()>=4096)throw new IllegalStateException("Native material pipeline cache bound exceeded");
        long[] stagesCode=modules.computeIfAbsent(k.code,c->new long[]{module(c.vertex()),module(c.fragment())});
        try(MemoryStack s=MemoryStack.stackPush()){
            var stages=VkPipelineShaderStageCreateInfo.calloc(2,s);for(int i=0;i<2;i++)stages.get(i).sType$Default().stage(i==0?VK_SHADER_STAGE_VERTEX_BIT:VK_SHADER_STAGE_FRAGMENT_BIT).module(stagesCode[i]).pName(s.UTF8("main"));
            var binding=VkVertexInputBindingDescription.calloc(1,s).binding(0).stride(36).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            var attrs=VkVertexInputAttributeDescription.calloc(6,s);int[] formats={VK_FORMAT_R32G32B32_SFLOAT,VK_FORMAT_R8G8B8A8_UNORM,VK_FORMAT_R32G32_SFLOAT,VK_FORMAT_R16G16_SSCALED,VK_FORMAT_R16G16_USCALED,VK_FORMAT_R8G8B8_SNORM},offsets={0,12,16,24,28,32};
            for(int i=0;i<6;i++)attrs.get(i).location(i).binding(0).format(formats[i]).offset(offsets[i]);
            var input=VkPipelineVertexInputStateCreateInfo.calloc(s).sType$Default().pVertexBindingDescriptions(binding).pVertexAttributeDescriptions(attrs);
            var assembly=VkPipelineInputAssemblyStateCreateInfo.calloc(s).sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            var viewport=VkPipelineViewportStateCreateInfo.calloc(s).sType$Default().viewportCount(1).scissorCount(1);
            var raster=VkPipelineRasterizationStateCreateInfo.calloc(s).sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(k.cull).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1)
                .depthBiasEnable(k.offset).depthBiasConstantFactor(k.offset?-10:0).depthBiasSlopeFactor(k.offset?-1:0);
            var multisample=VkPipelineMultisampleStateCreateInfo.calloc(s).sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            int compare=switch(k.depth){case OFF,ALWAYS->VK_COMPARE_OP_ALWAYS;case NEVER->VK_COMPARE_OP_NEVER;case LESS->VK_COMPARE_OP_LESS;case EQUAL->VK_COMPARE_OP_EQUAL;case LEQUAL->VK_COMPARE_OP_LESS_OR_EQUAL;case GREATER->VK_COMPARE_OP_GREATER;case NOTEQUAL->VK_COMPARE_OP_NOT_EQUAL;case GEQUAL->VK_COMPARE_OP_GREATER_OR_EQUAL;};
            var depth=VkPipelineDepthStencilStateCreateInfo.calloc(s).sType$Default().depthTestEnable(k.depth!=DepthTest.OFF).depthWriteEnable(k.writes.depth()).depthCompareOp(compare);
            NativeStencilPipelineState.apply(depth,k.stencil,k.depthFormat);
            var attachment=VkPipelineColorBlendAttachmentState.calloc(1,s).colorWriteMask(k.writes.color()?k.externalColor:0);
            attachment.blendEnable(k.blend!=Transparency.OPAQUE).colorBlendOp(VK_BLEND_OP_ADD).alphaBlendOp(VK_BLEND_OP_ADD).srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            int src=VK_BLEND_FACTOR_SRC_ALPHA,dst=VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            switch(k.blend){case OPAQUE-> {src=VK_BLEND_FACTOR_ONE;dst=VK_BLEND_FACTOR_ZERO;}case ADDITIVE->{src=VK_BLEND_FACTOR_ONE;dst=VK_BLEND_FACTOR_ONE;}case LIGHTNING->{src=VK_BLEND_FACTOR_SRC_ALPHA;dst=VK_BLEND_FACTOR_ONE;}case GLINT->{src=VK_BLEND_FACTOR_SRC_COLOR;dst=VK_BLEND_FACTOR_ONE;}case CRUMBLING->{src=VK_BLEND_FACTOR_DST_COLOR;dst=VK_BLEND_FACTOR_SRC_COLOR;}default->{}}
            attachment.srcColorBlendFactor(src).dstColorBlendFactor(dst);
            if(k.blend==Transparency.CRUMBLING)attachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
            if(k.blend==Transparency.GLINT)attachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ZERO).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
            if(k.blend==Transparency.ADDITIVE)attachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
            if(k.blend==Transparency.LIGHTNING)attachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
            var blend=VkPipelineColorBlendStateCreateInfo.calloc(s).sType$Default().pAttachments(attachment);
            var dynamic=VkPipelineDynamicStateCreateInfo.calloc(s).sType$Default().pDynamicStates(s.ints(VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR,VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK,VK_DYNAMIC_STATE_STENCIL_WRITE_MASK,VK_DYNAMIC_STATE_STENCIL_REFERENCE));
            var info=VkGraphicsPipelineCreateInfo.calloc(1,s).sType$Default().pStages(stages).pVertexInputState(input).pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster).pMultisampleState(multisample).pDepthStencilState(depth).pColorBlendState(blend).pDynamicState(dynamic).layout(layout).renderPass(k.pass);
            if(k.pass==0){var framebuffer=Renderer.getInstance().getBoundRenderPass().getFramebuffer();var rendering=VkPipelineRenderingCreateInfo.calloc(s).sType$Default().pColorAttachmentFormats(s.ints(framebuffer.getFormat())).depthAttachmentFormat(k.depthFormat).stencilAttachmentFormat(NativeStencilPipelineState.attachmentFormat(k.depthFormat));info.pNext(rendering.address());}
            LongBuffer out=s.mallocLong(1);check(vkCreateGraphicsPipelines(device,0,info,null,out),"original material pipeline");return out.get(0);
        }
    }
    private void retirePrograms(){ArrayList<Long> oldP=new ArrayList<>(pipelines.values());ArrayList<long[]> oldM=new ArrayList<>(modules.values());pipelines.clear();modules.clear();
        ArrayList<long[]> oldC=new ArrayList<>(computePrograms.values());computePrograms.clear();
        if(!oldP.isEmpty()||!oldM.isEmpty()||!oldC.isEmpty())MemoryManager.getInstance().addFrameOp(()->{for(long p:oldP)vkDestroyPipeline(device,p,null);for(long[] m:oldM)for(long h:m)vkDestroyShaderModule(device,h,null);for(long[] c:oldC)destroyCompute(c);});}
    public void delete(){live();deleted=true;if(current==this)current=null;MemoryManager.getInstance().addFrameOp(this::destroy);}
    private void destroy(){for(long p:pipelines.values())vkDestroyPipeline(device,p,null);pipelines.clear();for(long[] m:modules.values())for(long h:m)vkDestroyShaderModule(device,h,null);modules.clear();
        for(GpuMesh m:meshes.values()){m.vertices.destroy();m.indices.destroy();}meshes.clear();for(Frame f:frames)f.destroy();frames.clear();for(long s:samplers.values())vkDestroySampler(device,s,null);samplers.clear();
        for(long[] c:computePrograms.values())destroyCompute(c);computePrograms.clear();prepared.clear();if(computeLayout!=0){vkDestroyPipelineLayout(device,computeLayout,null);computeLayout=0;}if(computeStorageLayout!=0){vkDestroyDescriptorSetLayout(device,computeStorageLayout,null);computeStorageLayout=0;}
        if(layout!=0){vkDestroyPipelineLayout(device,layout,null);layout=0;}for(int i=0;i<3;i++)if(layouts[i]!=0){vkDestroyDescriptorSetLayout(device,layouts[i],null);layouts[i]=0;}}
    private final class GpuMesh {
        final Buffer vertices,indices;
        GpuMesh(Mesh mesh){vertices=new Buffer(Math.multiplyExact(mesh.vertexCount(),36),VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);Buffer index=null;try{
            FullVertexView view=new FullVertexView();view.ptr(MemoryUtil.memAddress(vertices.mapped));view.vertexCount(mesh.vertexCount());mesh.write(view);
            index=new Buffer(Math.multiplyExact(mesh.indexCount(),4),VK_BUFFER_USAGE_INDEX_BUFFER_BIT);mesh.indexSequence().fill(MemoryUtil.memAddress(index.mapped),mesh.indexCount());indices=index;
        }catch(Throwable e){vertices.destroy();if(index!=null)index.destroy();throw e;}}
    }
    private final class Frame {
        long epoch=-1;final ArrayList<Buffer> chunks=new ArrayList<>();final ArrayList<Long> pools=new ArrayList<>();int chunk,offset,pool,sets;
        final ArrayList<Prepared> feedback=new ArrayList<>();
        final ArrayList<LightSnapshot> lightVersions=new ArrayList<>();LightSnapshot latestLight;long lightUseEpoch=-1;
        void lightSnapshot(LightStorage light){
            long revision=light.nativeLightRevision();int bytes=Math.toIntExact(light.arena.byteCapacity());
            if(latestLight!=null&&latestLight.revision==revision){
                lightLut=latestLight.lut;lightSections=latestLight.sections;lightUseEpoch=epoch;NativeEngine.LIGHT_CACHE_HITS.incrementAndGet();return;
            }
            var lut=light.createLut();int lutBytes=Math.max(4,Math.multiplyExact(lut.size(),4));int sectionBytes=Math.max(4,bytes);
            // A previously referenced snapshot is immutable for the entire epoch.
            // A new epoch starts only after Hari has waited this frame's fence.
            if(latestLight==null||lightUseEpoch==epoch||latestLight.lut.buffer.bytes<lutBytes||latestLight.sections.buffer.bytes<sectionBytes){
                latestLight=new LightSnapshot(lutBytes,sectionBytes);lightVersions.add(latestLight);
            }
            latestLight.lut=new Slice(latestLight.lut.buffer,0,lutBytes);latestLight.sections=new Slice(latestLight.sections.buffer,0,sectionBytes);
            MemoryUtil.memSet(latestLight.lut.pointer(),0,lutBytes);for(int i=0;i<lut.size();i++)MemoryUtil.memPutInt(latestLight.lut.pointer()+i*4L,lut.getInt(i));
            MemoryUtil.memSet(latestLight.sections.pointer(),0,sectionBytes);MemoryUtil.memCopy(light.arena.indexToPointer(0),latestLight.sections.pointer(),bytes);
            latestLight.revision=revision;lightUseEpoch=epoch;lightLut=latestLight.lut;lightSections=latestLight.sections;
            NativeEngine.LIGHT_UPLOADS.incrementAndGet();NativeEngine.LIGHT_UPLOAD_BYTES.addAndGet((long)lutBytes+sectionBytes);
        }
        void begin(long next){if(epoch==next)return;long visible=0,submitted=0;for(var p:feedback){int count=MemoryUtil.memGetInt(p.models.pointer());if(count<0||count>p.submitted)throw new IllegalStateException("Native cull output exceeded original visible slots");visible+=count;submitted+=p.submitted;}if(epoch>=0){NativeEngine.GPU_LAST_VISIBLE.set(visible);NativeEngine.GPU_LAST_SUBMITTED.set(submitted);}feedback.clear();for(var it=lightVersions.iterator();it.hasNext();){var old=it.next();if(old!=latestLight){old.destroy();it.remove();}}epoch=next;chunk=offset=pool=sets=0;for(long p:pools)check(vkResetDescriptorPool(device,p,0),"frame descriptor reset after fence");}
        Slice allocate(int bytes){if(bytes<4||bytes>64*1024*1024)throw new IllegalArgumentException("Native frame snapshot extent");int align=(int)Math.max(16,Math.max(DeviceManager.deviceProperties.limits().minUniformBufferOffsetAlignment(),DeviceManager.deviceProperties.limits().minStorageBufferOffsetAlignment()));
            offset=(offset+align-1)&-align;while(chunk<chunks.size()&&offset+bytes>chunks.get(chunk).bytes){chunk++;offset=0;}
            if(chunk==chunks.size())chunks.add(new Buffer(Math.max(4*1024*1024,(bytes+align-1)&-align),VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT|VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT));
            Slice result=new Slice(chunks.get(chunk),offset,bytes);offset=Math.addExact(offset,bytes);MemoryUtil.memSet(result.pointer(),0,bytes);return result;}
        long[] descriptors(){return descriptors(layouts);}
        long[] descriptors(long[] requested){if(sets>=256){pool++;sets=0;}if(pool==pools.size())pools.add(createPool());try(MemoryStack s=MemoryStack.stackPush()){
            LongBuffer out=s.mallocLong(requested.length);check(vkAllocateDescriptorSets(device,VkDescriptorSetAllocateInfo.calloc(s).sType$Default().descriptorPool(pools.get(pool)).pSetLayouts(s.longs(requested)),out),"frame material/compute descriptors");sets++;long[] result=new long[requested.length];for(int i=0;i<result.length;i++)result[i]=out.get(i);return result;}}
        long createPool(){try(MemoryStack s=MemoryStack.stackPush()){var sizes=VkDescriptorPoolSize.calloc(3,s);sizes.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(256*6);sizes.get(1).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(256*8);sizes.get(2).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(256*4);
            LongBuffer out=s.mallocLong(1);check(vkCreateDescriptorPool(device,VkDescriptorPoolCreateInfo.calloc(s).sType$Default().maxSets(256*3).pPoolSizes(sizes),null,out),"frame descriptor pool");return out.get(0);}}
        void destroy(){for(var light:lightVersions)light.destroy();lightVersions.clear();latestLight=null;for(long p:pools)vkDestroyDescriptorPool(device,p,null);for(Buffer b:chunks)b.destroy();pools.clear();chunks.clear();}
    }
    private final class LightSnapshot {
        long revision=-1;Slice lut,sections;
        LightSnapshot(int lutBytes,int sectionBytes){Buffer lookup=new Buffer(lutBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);Buffer arena=null;try{arena=new Buffer(sectionBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);lut=new Slice(lookup,0,lutBytes);sections=new Slice(arena,0,sectionBytes);}catch(Throwable failure){lookup.destroy();if(arena!=null)arena.destroy();throw failure;}}
        void destroy(){lut.buffer.destroy();sections.buffer.destroy();}
    }
    private final class Buffer {
        final int bytes;long buffer,memory;ByteBuffer mapped;
        Buffer(int bytes,int usage){if(bytes<4||bytes>64*1024*1024)throw new IllegalArgumentException("Native buffer extent");this.bytes=bytes;try(MemoryStack s=MemoryStack.stackPush()){
            LongBuffer out=s.mallocLong(1);check(vkCreateBuffer(device,VkBufferCreateInfo.calloc(s).sType$Default().size(bytes).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE),null,out),"buffer");buffer=out.get(0);
            var req=VkMemoryRequirements.malloc(s);vkGetBufferMemoryRequirements(device,buffer,req);var props=VkPhysicalDeviceMemoryProperties.malloc(s);vkGetPhysicalDeviceMemoryProperties(device.getPhysicalDevice(),props);int index=-1,flags=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            for(int i=0;i<props.memoryTypeCount();i++)if((req.memoryTypeBits()&(1<<i))!=0&&(props.memoryTypes(i).propertyFlags()&flags)==flags){index=i;break;}if(index<0)throw new IllegalStateException("Coherent native frame memory unavailable");
            check(vkAllocateMemory(device,VkMemoryAllocateInfo.calloc(s).sType$Default().allocationSize(req.size()).memoryTypeIndex(index),null,out),"buffer memory");memory=out.get(0);check(vkBindBufferMemory(device,buffer,memory,0),"buffer binding");PointerBuffer ptr=s.mallocPointer(1);check(vkMapMemory(device,memory,0,bytes,0,ptr),"buffer map");mapped=MemoryUtil.memByteBuffer(ptr.get(0),bytes).order(ByteOrder.LITTLE_ENDIAN);MemoryUtil.memSet(ptr.get(0),0,bytes);
        }catch(Throwable e){destroy();throw e;}}
        void destroy(){if(mapped!=null){vkUnmapMemory(device,memory);mapped=null;}if(buffer!=0){vkDestroyBuffer(device,buffer,null);buffer=0;}if(memory!=0){vkFreeMemory(device,memory,null);memory=0;}}
    }
    private static void check(int result,String operation){if(result!=VK_SUCCESS)throw new IllegalStateException(operation+" VkResult="+result);}
}
