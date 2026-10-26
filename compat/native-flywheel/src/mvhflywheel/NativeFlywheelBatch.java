package mvhflywheel;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import net.vulkanmod.vulkan.shader.NativeStencilPipelineState;
import java.nio.*;
import java.util.*;
import java.util.function.Consumer;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO;

/**
 * Original native Vulkan batch: Flywheel storage ABI, compute-generated indexed
 * instance commands, actual textures, explicit barriers and deferred ownership.
 * GPL-3.0-only. No OpenGL call, synthetic program handle or compatibility gate.
 * The caller owns the current graphics command buffer and render-pass boundaries.
 */
public final class NativeFlywheelBatch {
    public static final int MODEL_STRIDE=28,DRAW_STRIDE=36,TRANSFORMED_STRIDE=76,VERTEX_STRIDE=36;
    public enum Kind { MODEL,COMMAND,INSTANCE,TARGET,VERTEX,INDEX,PAGE,MATRIX,FRAME }
    /** Stencil operations/depth/color select a pipeline; masks/reference stay dynamic. */
    public record DrawState(int stencil,int compareMask,int writeMask,int reference,
                            boolean depthTest,boolean depthWrite,int depthCompare,int colorMask) {
        public static final DrawState OPAQUE=new DrawState(0,0xff,0xff,0,true,true,VK_COMPARE_OP_LESS_OR_EQUAL,15);
        public DrawState {
            if((stencil&~0x1fff)!=0||depthCompare<0||depthCompare>7||(colorMask&~15)!=0)
                throw new IllegalArgumentException("Unsupported native depth/stencil/color state");
        }
    }
    private record GraphicsKey(int stencil,boolean depthTest,boolean depthWrite,int depthCompare,int colorMask) {
        GraphicsKey(DrawState state){this((state.stencil()&1)==0?0:state.stencil(),state.depthTest(),state.depthWrite(),state.depthCompare(),state.colorMask());}
    }
    private final Thread owner=Thread.currentThread();
    private final VkDevice device;
    private final EnumMap<Kind,Storage> buffers=new EnumMap<>(Kind.class);
    private final List<Long> descriptorLayouts=new ArrayList<>(),pipelineLayouts=new ArrayList<>(),pipelines=new ArrayList<>(),modules=new ArrayList<>();
    private long pool,computeSet,graphicsSet,textureSet,computeLayout,graphicsLayout,computePipeline,graphicsPipeline;
    private long cullSet,cullFrameSet,cullDepthSet,cullLayout,cullPipeline;
    private long vertexModule,fragmentModule,renderPass;
    private int colorFormat,depthFormat;
    private final Map<GraphicsKey,Long> graphicsVariants=new HashMap<>();
    private boolean retired;
    private int dispatches,draws;

    public NativeFlywheelBatch(VkDevice device,byte[] compute,byte[] vertex,byte[] fragment,long renderPass,int colorFormat,int depthFormat,
                              long[] views,long[] samplers,int vertexBytes,int indexBytes,int instanceBytes,int targetBytes,int modelBytes,int drawBytes) {
        this.device=Objects.requireNonNull(device);
        if(views.length!=2||samplers.length!=2)throw new IllegalArgumentException("Two original model texture bindings required");
        if(modelBytes%MODEL_STRIDE!=0||drawBytes%DRAW_STRIDE!=0||instanceBytes%TRANSFORMED_STRIDE!=0||vertexBytes%VERTEX_STRIDE!=0||indexBytes%4!=0||targetBytes%4!=0)throw new IllegalArgumentException("Original batch ABI extent mismatch");
        try(MemoryStack s=MemoryStack.stackPush()){
            buffers.put(Kind.MODEL,new Storage(modelBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT));
            buffers.put(Kind.COMMAND,new Storage(drawBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT));
            buffers.put(Kind.INSTANCE,new Storage(instanceBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT));
            buffers.put(Kind.TARGET,new Storage(targetBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT));
            buffers.put(Kind.VERTEX,new Storage(vertexBytes,VK_BUFFER_USAGE_VERTEX_BUFFER_BIT));
            buffers.put(Kind.INDEX,new Storage(indexBytes,VK_BUFFER_USAGE_INDEX_BUFFER_BIT));
            long c=descriptorLayout(s,new int[]{3,4},VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,VK_SHADER_STAGE_COMPUTE_BIT);
            long g=descriptorLayout(s,new int[]{1,2,4},VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,VK_SHADER_STAGE_VERTEX_BIT);
            long t=descriptorLayout(s,new int[]{0,1},VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorPoolSize.Buffer sizes=VkDescriptorPoolSize.calloc(3,s);
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(10);sizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(3);sizes.get(2).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1);
            LongBuffer handle=s.mallocLong(1);check(vkCreateDescriptorPool(device,VkDescriptorPoolCreateInfo.calloc(s).sType$Default().maxSets(6).pPoolSizes(sizes),null,handle),"Create descriptor pool");pool=handle.get(0);
            LongBuffer sets=s.mallocLong(3);check(vkAllocateDescriptorSets(device,VkDescriptorSetAllocateInfo.calloc(s).sType$Default().descriptorPool(pool).pSetLayouts(s.longs(c,g,t)),sets),"Allocate descriptor sets");
            computeSet=sets.get(0);graphicsSet=sets.get(1);textureSet=sets.get(2);
            writeBuffer(s,computeSet,3,Kind.MODEL);writeBuffer(s,computeSet,4,Kind.COMMAND);
            writeBuffer(s,graphicsSet,1,Kind.INSTANCE);writeBuffer(s,graphicsSet,2,Kind.TARGET);writeBuffer(s,graphicsSet,4,Kind.COMMAND);
            for(int i=0;i<2;i++){
                VkDescriptorImageInfo.Buffer info=VkDescriptorImageInfo.calloc(1,s).sampler(samplers[i]).imageView(views[i]).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                VkWriteDescriptorSet.Buffer write=VkWriteDescriptorSet.calloc(1,s).sType$Default().dstSet(textureSet).dstBinding(i).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1).pImageInfo(info);
                vkUpdateDescriptorSets(device,write,null);
            }
            computeLayout=pipelineLayout(s,new long[]{c},false);graphicsLayout=pipelineLayout(s,new long[]{g,t},true);
            long cm=module(s,compute),vm=module(s,vertex),fm=module(s,fragment);
            VkComputePipelineCreateInfo.Buffer cp=VkComputePipelineCreateInfo.calloc(1,s).sType$Default().layout(computeLayout);
            cp.stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(cm).pName(s.UTF8("main"));
            check(vkCreateComputePipelines(device,0,cp,null,handle),"Create actual Flywheel compute pipeline");computePipeline=handle.get(0);pipelines.add(computePipeline);
            this.vertexModule=vm;this.fragmentModule=fm;this.renderPass=renderPass;this.colorFormat=colorFormat;this.depthFormat=depthFormat;
            GraphicsKey opaque=new GraphicsKey(DrawState.OPAQUE);
            graphicsPipeline=createGraphics(s,opaque);pipelines.add(graphicsPipeline);graphicsVariants.put(opaque,graphicsPipeline);
        }catch(Throwable failure){destroy();throw failure;}
    }
    public ByteBuffer mapped(Kind kind){owned();return buffers.get(kind).mapped.duplicate().order(ByteOrder.LITTLE_ENDIAN);}
    public int dispatches(){return dispatches;}
    public int draws(){return draws;}
    /** Original cull shader: separate descriptor namespaces retain original binding numbers. */
    public void configureCulling(byte[] shader,long depthView,long depthSampler,int frameBytes,int pageBytes,int matrixBytes){
        owned();if(cullPipeline!=0||dispatches!=0||draws!=0)throw new IllegalStateException("Culling must be configured before submission");
        if(pageBytes%8!=0||matrixBytes%112!=0||frameBytes%16!=0)throw new IllegalArgumentException("Original culling ABI extent mismatch");
        buffers.put(Kind.PAGE,new Storage(pageBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT));buffers.put(Kind.MATRIX,new Storage(matrixBytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT));buffers.put(Kind.FRAME,new Storage(frameBytes,VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT));
        try(MemoryStack s=MemoryStack.stackPush()){
            long storage=descriptorLayout(s,new int[]{0,1,2,3,7},VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,VK_SHADER_STAGE_COMPUTE_BIT);
            long frame=descriptorLayout(s,new int[]{0},VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,VK_SHADER_STAGE_COMPUTE_BIT);
            long depth=descriptorLayout(s,new int[]{0},VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,VK_SHADER_STAGE_COMPUTE_BIT);
            LongBuffer sets=s.mallocLong(3);check(vkAllocateDescriptorSets(device,VkDescriptorSetAllocateInfo.calloc(s).sType$Default().descriptorPool(pool).pSetLayouts(s.longs(storage,frame,depth)),sets),"Allocate native cull descriptors");cullSet=sets.get(0);cullFrameSet=sets.get(1);cullDepthSet=sets.get(2);
            writeBuffer(s,cullSet,0,Kind.PAGE);writeBuffer(s,cullSet,1,Kind.INSTANCE);writeBuffer(s,cullSet,2,Kind.TARGET);writeBuffer(s,cullSet,3,Kind.MODEL);writeBuffer(s,cullSet,7,Kind.MATRIX);
            writeBuffer(s,cullFrameSet,0,Kind.FRAME,VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            VkDescriptorImageInfo.Buffer image=VkDescriptorImageInfo.calloc(1,s).imageView(depthView).sampler(depthSampler).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            vkUpdateDescriptorSets(device,VkWriteDescriptorSet.calloc(1,s).sType$Default().dstSet(cullDepthSet).dstBinding(0).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(image),null);
            cullLayout=pipelineLayout(s,new long[]{storage,frame,depth},false);long code=module(s,shader);
            VkComputePipelineCreateInfo.Buffer info=VkComputePipelineCreateInfo.calloc(1,s).sType$Default().layout(cullLayout);info.stage().sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(code).pName(s.UTF8("main"));
            LongBuffer out=s.mallocLong(1);check(vkCreateComputePipelines(device,0,info,null,out),"Create original Flywheel native cull pipeline");cullPipeline=out.get(0);pipelines.add(cullPipeline);
        }
    }
    /** Outside rendering; caller resets model counts only after the preceding frame's fence. */
    public void cullVisibleInstances(VkCommandBuffer command,int pages){
        owned();if(cullPipeline==0||pages<1||pages>buffers.get(Kind.PAGE).bytes/8)throw new IllegalArgumentException("Culling page extent");
        try(MemoryStack s=MemoryStack.stackPush()){
            VkMemoryBarrier.Buffer host=VkMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(VK_ACCESS_HOST_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT|VK_ACCESS_UNIFORM_READ_BIT);
            vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,host,null,null);
            vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_COMPUTE,cullPipeline);vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_COMPUTE,cullLayout,0,s.longs(cullSet,cullFrameSet,cullDepthSet),null);vkCmdDispatch(command,pages,1,1);dispatches++;
            VkBufferMemoryBarrier.Buffer barriers=VkBufferMemoryBarrier.calloc(2,s);Kind[] written={Kind.MODEL,Kind.TARGET};
            for(int i=0;i<2;i++)barriers.get(i).sType$Default().srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_HOST_READ_BIT).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).buffer(buffers.get(written[i]).buffer).offset(0).size(VK_WHOLE_SIZE);
            vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_VERTEX_SHADER_BIT|VK_PIPELINE_STAGE_HOST_BIT,0,null,barriers,null);
        }
    }
    /** Must be called outside rendering, after previous mapped writes are fence-complete. */
    public void applyInstanceCounts(VkCommandBuffer command){
        owned();try(MemoryStack s=MemoryStack.stackPush()){
            VkMemoryBarrier.Buffer host=VkMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(VK_ACCESS_HOST_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT|VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT|VK_ACCESS_INDEX_READ_BIT|VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_VERTEX_INPUT_BIT|VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT|VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,0,host,null,null);
            vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_COMPUTE,computePipeline);
            vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_COMPUTE,computeLayout,0,s.longs(computeSet),null);
            int count=buffers.get(Kind.COMMAND).bytes/DRAW_STRIDE;vkCmdDispatch(command,(count+31)/32,1,1);dispatches++;
            VkBufferMemoryBarrier.Buffer barrier=VkBufferMemoryBarrier.calloc(1,s).sType$Default().srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT|VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_HOST_READ_BIT).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).buffer(buffers.get(Kind.COMMAND).buffer).offset(0).size(VK_WHOLE_SIZE);
            vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT|VK_PIPELINE_STAGE_VERTEX_SHADER_BIT|VK_PIPELINE_STAGE_HOST_BIT,0,null,barrier,null);
        }
    }
    public void drawIndirect(VkCommandBuffer command,FloatBuffer projection,int drawIndex){
        drawIndirect(command,projection,drawIndex,DrawState.OPAQUE);
    }
    public void drawIndirect(VkCommandBuffer command,FloatBuffer projection,int drawIndex,DrawState state){
        owned();if(drawIndex<0||drawIndex>=buffers.get(Kind.COMMAND).bytes/DRAW_STRIDE)throw new IllegalArgumentException("Draw index outside ABI buffer");
        bind(command,projection,state);vkCmdDrawIndexedIndirect(command,buffers.get(Kind.COMMAND).buffer,(long)drawIndex*DRAW_STRIDE,1,DRAW_STRIDE);draws++;
    }
    /** Independent explicit draw reference uses the same original mesh/instance/material inputs. */
    public void drawExplicit(VkCommandBuffer command,FloatBuffer projection,int count,int instances,int firstIndex,int vertexOffset,int firstInstance){
        drawExplicit(command,projection,count,instances,firstIndex,vertexOffset,firstInstance,DrawState.OPAQUE);
    }
    public void drawExplicit(VkCommandBuffer command,FloatBuffer projection,int count,int instances,int firstIndex,int vertexOffset,int firstInstance,DrawState state){
        owned();bind(command,projection,state);vkCmdDrawIndexed(command,count,instances,firstIndex,vertexOffset,firstInstance);draws++;
    }
    private void bind(VkCommandBuffer command,FloatBuffer projection,DrawState state){
        Objects.requireNonNull(state);
        if(projection.remaining()!=16)throw new IllegalArgumentException("Projection must have16 floats");
        if((state.stencil()&1)!=0&&NativeStencilPipelineState.attachmentFormat(depthFormat)==VK_FORMAT_UNDEFINED)
            throw new IllegalStateException("Native clipping requires a stencil attachment");
        try(MemoryStack s=MemoryStack.stackPush()){
            GraphicsKey key=new GraphicsKey(state);Long pipeline=graphicsVariants.get(key);
            if(pipeline==null){
                if(graphicsVariants.size()>=256)throw new IllegalStateException("Native graphics variant limit");
                pipeline=createGraphics(s,key);pipelines.add(pipeline);graphicsVariants.put(key,pipeline);
            }
            vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
            vkCmdSetStencilCompareMask(command,VK_STENCIL_FACE_FRONT_AND_BACK,state.compareMask());
            vkCmdSetStencilWriteMask(command,VK_STENCIL_FACE_FRONT_AND_BACK,state.writeMask());
            vkCmdSetStencilReference(command,VK_STENCIL_FACE_FRONT_AND_BACK,state.reference());
            vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_GRAPHICS,graphicsLayout,0,s.longs(graphicsSet,textureSet),null);
            vkCmdPushConstants(command,graphicsLayout,VK_SHADER_STAGE_VERTEX_BIT,0,projection);
            vkCmdBindVertexBuffers(command,0,s.longs(buffers.get(Kind.VERTEX).buffer),s.longs(0));
            vkCmdBindIndexBuffer(command,buffers.get(Kind.INDEX).buffer,0,VK_INDEX_TYPE_UINT32);
        }
    }
    /** Retires only this batch; caller uses the renderer's existing fence-delayed resource queue. */
    public void retire(Consumer<Runnable> afterFrame){owned();retired=true;afterFrame.accept(this::destroy);}
    private void owned(){if(retired||Thread.currentThread()!=owner)throw new IllegalStateException("Native batch lifetime/render owner violation");}
    private void check(int result,String operation){if(result!=VK_SUCCESS)throw new IllegalStateException(operation+": VkResult="+result);}
    private long descriptorLayout(MemoryStack s,int[] indexes,int type,int stages){
        VkDescriptorSetLayoutBinding.Buffer bindings=VkDescriptorSetLayoutBinding.calloc(indexes.length,s);
        for(int i=0;i<indexes.length;i++)bindings.get(i).binding(indexes[i]).descriptorType(type).descriptorCount(1).stageFlags(stages);
        LongBuffer out=s.mallocLong(1);check(vkCreateDescriptorSetLayout(device,VkDescriptorSetLayoutCreateInfo.calloc(s).sType$Default().pBindings(bindings),null,out),"Create sparse descriptor layout");long id=out.get(0);descriptorLayouts.add(id);return id;
    }
    private void writeBuffer(MemoryStack s,long set,int binding,Kind kind){
        writeBuffer(s,set,binding,kind,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
    }
    private void writeBuffer(MemoryStack s,long set,int binding,Kind kind,int descriptorType){
        Storage buffer=buffers.get(kind);VkDescriptorBufferInfo.Buffer info=VkDescriptorBufferInfo.calloc(1,s).buffer(buffer.buffer).offset(0).range(buffer.bytes);
        VkWriteDescriptorSet.Buffer write=VkWriteDescriptorSet.calloc(1,s).sType$Default().dstSet(set).dstBinding(binding).descriptorType(descriptorType).descriptorCount(1).pBufferInfo(info);vkUpdateDescriptorSets(device,write,null);
    }
    private long pipelineLayout(MemoryStack s,long[] layouts,boolean projection){
        VkPipelineLayoutCreateInfo info=VkPipelineLayoutCreateInfo.calloc(s).sType$Default().pSetLayouts(s.longs(layouts));
        if(projection)info.pPushConstantRanges(VkPushConstantRange.calloc(1,s).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(64));
        LongBuffer out=s.mallocLong(1);check(vkCreatePipelineLayout(device,info,null,out),"Create pipeline layout");long id=out.get(0);pipelineLayouts.add(id);return id;
    }
    private long module(MemoryStack s,byte[] code){
        if(code.length<20||(code.length&3)!=0||code.length>16*1024*1024)throw new IllegalArgumentException("Invalid shader bytecode extent");
        ByteBuffer bytes=MemoryUtil.memAlloc(code.length).put(code).flip();try{
            if(bytes.order(ByteOrder.LITTLE_ENDIAN).getInt(0)!=0x07230203)throw new IllegalArgumentException("Invalid shader bytecode magic");
            LongBuffer out=s.mallocLong(1);check(vkCreateShaderModule(device,VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(bytes),null,out),"Create native shader module");long id=out.get(0);modules.add(id);return id;
        }finally{MemoryUtil.memFree(bytes);}
    }
    private long createGraphics(MemoryStack s,GraphicsKey state){
        VkPipelineShaderStageCreateInfo.Buffer stages=VkPipelineShaderStageCreateInfo.calloc(2,s);
        stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(s.UTF8("main"));stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule).pName(s.UTF8("main"));
        VkVertexInputBindingDescription.Buffer binding=VkVertexInputBindingDescription.calloc(1,s).binding(0).stride(VERTEX_STRIDE).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        VkVertexInputAttributeDescription.Buffer attrs=VkVertexInputAttributeDescription.calloc(4,s);
        int[] formats={VK_FORMAT_R32G32B32_SFLOAT,VK_FORMAT_R32G32_SFLOAT,VK_FORMAT_R32G32B32_SFLOAT,VK_FORMAT_R32_UINT},offsets={0,12,20,32};
        for(int i=0;i<4;i++)attrs.get(i).location(i).binding(0).format(formats[i]).offset(offsets[i]);
        VkPipelineVertexInputStateCreateInfo input=VkPipelineVertexInputStateCreateInfo.calloc(s).sType$Default().pVertexBindingDescriptions(binding).pVertexAttributeDescriptions(attrs);
        VkPipelineInputAssemblyStateCreateInfo assembly=VkPipelineInputAssemblyStateCreateInfo.calloc(s).sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        VkPipelineViewportStateCreateInfo viewport=VkPipelineViewportStateCreateInfo.calloc(s).sType$Default().viewportCount(1).scissorCount(1);
        VkPipelineRasterizationStateCreateInfo raster=VkPipelineRasterizationStateCreateInfo.calloc(s).sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_BACK_BIT).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1);
        VkPipelineMultisampleStateCreateInfo multisample=VkPipelineMultisampleStateCreateInfo.calloc(s).sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        VkPipelineDepthStencilStateCreateInfo depth=VkPipelineDepthStencilStateCreateInfo.calloc(s).sType$Default().depthTestEnable(state.depthTest()).depthWriteEnable(state.depthWrite()).depthCompareOp(state.depthCompare());
        NativeStencilPipelineState.apply(depth,state.stencil(),depthFormat);
        VkPipelineColorBlendAttachmentState.Buffer attachment=VkPipelineColorBlendAttachmentState.calloc(1,s).colorWriteMask(state.colorMask()).blendEnable(false);
        VkPipelineColorBlendStateCreateInfo blend=VkPipelineColorBlendStateCreateInfo.calloc(s).sType$Default().pAttachments(attachment);
        VkPipelineDynamicStateCreateInfo dynamic=VkPipelineDynamicStateCreateInfo.calloc(s).sType$Default().pDynamicStates(s.ints(VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR,VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK,VK_DYNAMIC_STATE_STENCIL_WRITE_MASK,VK_DYNAMIC_STATE_STENCIL_REFERENCE));
        VkPipelineRenderingCreateInfo rendering=VkPipelineRenderingCreateInfo.calloc(s).sType(VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO).pColorAttachmentFormats(s.ints(colorFormat)).depthAttachmentFormat(depthFormat).stencilAttachmentFormat(depthFormat==VK_FORMAT_D32_SFLOAT_S8_UINT||depthFormat==VK_FORMAT_D24_UNORM_S8_UINT?depthFormat:VK_FORMAT_UNDEFINED);
        VkGraphicsPipelineCreateInfo.Buffer info=VkGraphicsPipelineCreateInfo.calloc(1,s).sType$Default().pStages(stages).pVertexInputState(input).pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster).pMultisampleState(multisample).pDepthStencilState(depth).pColorBlendState(blend).pDynamicState(dynamic).layout(graphicsLayout);
        if(renderPass!=0)info.renderPass(renderPass);else info.pNext(rendering.address());
        LongBuffer out=s.mallocLong(1);check(vkCreateGraphicsPipelines(device,0,info,null,out),"Create native indexed-instance graphics pipeline");return out.get(0);
    }
    private void destroy(){
        for(long p:pipelines)vkDestroyPipeline(device,p,null);pipelines.clear();graphicsVariants.clear();for(long p:modules)vkDestroyShaderModule(device,p,null);modules.clear();
        if(pool!=0){vkDestroyDescriptorPool(device,pool,null);pool=0;}for(long p:pipelineLayouts)vkDestroyPipelineLayout(device,p,null);pipelineLayouts.clear();for(long p:descriptorLayouts)vkDestroyDescriptorSetLayout(device,p,null);descriptorLayouts.clear();
        for(Storage b:buffers.values())b.destroy();buffers.clear();
    }
    private final class Storage {
        long buffer,memory;ByteBuffer mapped;final int bytes;
        Storage(int bytes,int usage){
            if(bytes<4||bytes>32*1024*1024||(bytes&3)!=0)throw new IllegalArgumentException("Native batch buffer extent limit");this.bytes=bytes;
            try(MemoryStack s=MemoryStack.stackPush()){
                LongBuffer out=s.mallocLong(1);check(vkCreateBuffer(device,VkBufferCreateInfo.calloc(s).sType$Default().size(bytes).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE),null,out),"Create batch buffer");buffer=out.get(0);
                VkMemoryRequirements requirements=VkMemoryRequirements.malloc(s);vkGetBufferMemoryRequirements(device,buffer,requirements);
                VkPhysicalDeviceMemoryProperties properties=VkPhysicalDeviceMemoryProperties.malloc(s);vkGetPhysicalDeviceMemoryProperties(device.getPhysicalDevice(),properties);
                int index=-1,flags=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
                for(int i=0;i<properties.memoryTypeCount();i++)if((requirements.memoryTypeBits()&(1<<i))!=0&&(properties.memoryTypes(i).propertyFlags()&flags)==flags){index=i;break;}
                if(index<0)throw new IllegalStateException("No coherent mapped memory type for original batch ABI");
                check(vkAllocateMemory(device,VkMemoryAllocateInfo.calloc(s).sType$Default().allocationSize(requirements.size()).memoryTypeIndex(index),null,out),"Allocate batch memory");memory=out.get(0);check(vkBindBufferMemory(device,buffer,memory,0),"Bind batch memory");
                PointerBuffer address=s.mallocPointer(1);check(vkMapMemory(device,memory,0,bytes,0,address),"Map batch memory");mapped=MemoryUtil.memByteBuffer(address.get(0),bytes).order(ByteOrder.LITTLE_ENDIAN);MemoryUtil.memSet(address.get(0),0,bytes);
            }catch(Throwable failure){destroy();throw failure;}
        }
        void destroy(){if(mapped!=null){vkUnmapMemory(device,memory);mapped=null;}if(buffer!=0){vkDestroyBuffer(device,buffer,null);buffer=0;}if(memory!=0){vkFreeMemory(device,memory,null);memory=0;}}
    }
}
