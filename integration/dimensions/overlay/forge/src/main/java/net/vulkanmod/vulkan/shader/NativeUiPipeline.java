package net.vulkanmod.vulkan.shader;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import net.vulkanmod.vulkan.util.VkResult;
import java.nio.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Original GPL-3.0-only native UI submission. The caller owns render-pass,
 * viewport/scissor, sampled-image transitions, submission and its completion
 * fence. Each packet owns immutable GPU data until that fence completes.
 * No arbitrary GL contract is accepted by this explicit native API.
 */
public final class NativeUiPipeline implements AutoCloseable {
    public record Texture(long view, long sampler) {
        public Texture { if (view == 0 || sampler == 0) throw new IllegalArgumentException("Native texture handles required"); }
    }
    private final VkDevice device;
    private final NativeUiProgram program;
    private final Thread owner = Thread.currentThread();
    private final Set<Packet> packets = new HashSet<>();
    private long vertexModule, fragmentModule, descriptorLayout, pipelineLayout, pipeline;
    private final int stride;
    private boolean closed;

    public NativeUiPipeline(VkDevice device, NativeUiProgram program, long renderPass) {
        this.device = Objects.requireNonNull(device); this.program = Objects.requireNonNull(program);
        if (renderPass == 0) throw new IllegalArgumentException("Actual compatible native UI render pass required");
        int bytes = 0; for (NativeUiProgram.Attribute a : program.attributes()) bytes = Math.addExact(bytes, components(a.type()) * 4);
        this.stride = bytes; if (stride == 0) throw new IllegalArgumentException("Native vertex layout required");
        try (MemoryStack s = MemoryStack.stackPush()) {
            vertexModule = module(s, program.vertexSpirv()); fragmentModule = module(s, program.fragmentSpirv());
            int count = program.samplers().size() + (program.uniformBytes() > 0 ? 1 : 0), at = 0;
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(count, s);
            if (program.uniformBytes() > 0) bindings.get(at++).binding(0).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            for (NativeUiProgram.Sampler sampler : program.samplers()) bindings.get(at++).binding(sampler.binding()).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            LongBuffer out = s.mallocLong(1);
            check(vkCreateDescriptorSetLayout(device, VkDescriptorSetLayoutCreateInfo.calloc(s).sType$Default().pBindings(bindings), null, out), "UI descriptor layout"); descriptorLayout = out.get(0);
            check(vkCreatePipelineLayout(device, VkPipelineLayoutCreateInfo.calloc(s).sType$Default().pSetLayouts(s.longs(descriptorLayout)), null, out), "UI pipeline layout"); pipelineLayout = out.get(0);
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, s);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(s.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule).pName(s.UTF8("main"));
            VkVertexInputBindingDescription.Buffer vb = VkVertexInputBindingDescription.calloc(1,s).binding(0).stride(stride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            VkVertexInputAttributeDescription.Buffer attrs = VkVertexInputAttributeDescription.calloc(program.attributes().size(),s);
            at = 0; int offset = 0;
            for (NativeUiProgram.Attribute a : program.attributes()) { attrs.get(at++).location(a.location()).binding(0).format(format(a.type())).offset(offset); offset += components(a.type()) * 4; }
            VkPipelineVertexInputStateCreateInfo input = VkPipelineVertexInputStateCreateInfo.calloc(s).sType$Default().pVertexBindingDescriptions(vb).pVertexAttributeDescriptions(attrs);
            VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(s).sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(s).sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(s).sType$Default().polygonMode(VK_POLYGON_MODE_FILL).cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1);
            VkPipelineMultisampleStateCreateInfo samples = VkPipelineMultisampleStateCreateInfo.calloc(s).sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(s).sType$Default().depthTestEnable(false).depthWriteEnable(false);
            VkPipelineColorBlendAttachmentState.Buffer color = VkPipelineColorBlendAttachmentState.calloc(1,s).colorWriteMask(15).blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA).dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).alphaBlendOp(VK_BLEND_OP_ADD);
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(s).sType$Default().pAttachments(color);
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(s).sType$Default().pDynamicStates(s.ints(VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR));
            VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1,s).sType$Default().pStages(stages).pVertexInputState(input).pInputAssemblyState(assembly)
                    .pViewportState(viewport).pRasterizationState(raster).pMultisampleState(samples).pDepthStencilState(depth).pColorBlendState(blend).pDynamicState(dynamic).layout(pipelineLayout).renderPass(renderPass);
            // A failed batch can still return owned non-null pipeline handles.
            // Capture its initialized output before propagating the result so the
            // constructor's cleanup retires every returned native resource.
            out.put(0, VK_NULL_HANDLE);
            int result = vkCreateGraphicsPipelines(device,0,info,null,out);
            pipeline = out.get(0);
            check(result,"UI graphics pipeline");
        } catch (Throwable failure) { destroy(); throw failure; }
    }
    public int vertexStride() { return stride; }
    public Packet packet(ByteBuffer vertices, ByteBuffer uniforms, Map<String,Texture> textures) {
        thread(); if (closed) throw new IllegalStateException("Native UI pipeline closed");
        if (vertices.remaining() == 0 || vertices.remaining() % stride != 0 || vertices.remaining() / stride % 3 != 0)
            throw new IllegalArgumentException("Exact native UI triangle layout required");
        if (uniforms.remaining() != program.uniformBytes()) throw new IllegalArgumentException("Reflected native UI uniform extent required");
        if (!textures.keySet().equals(new HashSet<>(program.samplers().stream().map(NativeUiProgram.Sampler::name).toList()))) throw new IllegalArgumentException("Exact original sampler bindings required");
        Packet p = new Packet(vertices, uniforms, textures); packets.add(p); return p;
    }
    public final class Packet implements AutoCloseable {
        private Storage vertex, uniform;
        private long pool, set, fence;
        private final int vertices;
        private boolean recorded, closed;
        private Packet(ByteBuffer v, ByteBuffer u, Map<String,Texture> textures) {
            vertices = v.remaining() / stride;
            try (MemoryStack s = MemoryStack.stackPush()) {
                vertex = new Storage(v,VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
                if (u.remaining() > 0) uniform = new Storage(u,VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
                int count = (uniform == null ? 0 : 1) + (textures.isEmpty() ? 0 : 1), at = 0;
                VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(count,s);
                if (uniform != null) sizes.get(at++).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1);
                if (!textures.isEmpty()) sizes.get(at).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(textures.size());
                LongBuffer out=s.mallocLong(1);
                check(vkCreateDescriptorPool(device,VkDescriptorPoolCreateInfo.calloc(s).sType$Default().maxSets(1).pPoolSizes(sizes),null,out),"UI packet descriptor pool"); pool=out.get(0);
                check(vkAllocateDescriptorSets(device,VkDescriptorSetAllocateInfo.calloc(s).sType$Default().descriptorPool(pool).pSetLayouts(s.longs(descriptorLayout)),out),"UI packet descriptors");set=out.get(0);
                VkWriteDescriptorSet.Buffer writes=VkWriteDescriptorSet.calloc((uniform==null?0:1)+textures.size(),s);at=0;
                if(uniform!=null)writes.get(at++).sType$Default().dstSet(set).dstBinding(0).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
                        .pBufferInfo(VkDescriptorBufferInfo.calloc(1,s).buffer(uniform.buffer).range(program.uniformBytes()));
                for(NativeUiProgram.Sampler sampler:program.samplers()){
                    Texture t=textures.get(sampler.name());writes.get(at++).sType$Default().dstSet(set).dstBinding(sampler.binding()).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                            .pImageInfo(VkDescriptorImageInfo.calloc(1,s).imageView(t.view).sampler(t.sampler).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
                }
                vkUpdateDescriptorSets(device,writes,null);
            } catch(Throwable failure){destroy();throw failure;}
        }
        public void record(VkCommandBuffer command) {
            thread(); if(closed||recorded)throw new IllegalStateException("UI packet already recorded/closed");
            try(MemoryStack s=MemoryStack.stackPush()){
                vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
                vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_GRAPHICS,pipelineLayout,0,s.longs(set),null);
                vkCmdBindVertexBuffers(command,0,s.longs(vertex.buffer),s.longs(0));vkCmdDraw(command,vertices,1,0,0);recorded=true;
            }
        }
        /** Caller must use the completion fence for the exact submission containing record(). */
        public void submitted(long completionFence) {
            thread();if(!recorded||closed||fence!=0||completionFence==0)throw new IllegalStateException("Exact submitted UI fence required");fence=completionFence;
        }
        @Override public void close() {
            thread();if(closed)return;
            if(recorded){if(fence==0)throw new IllegalStateException("Recorded UI packet has no submission completion fence");int result=vkGetFenceStatus(device,fence);
                if(result==VK_NOT_READY)throw new IllegalStateException("UI packet still in GPU use");check(result,"UI packet completion");}
            destroy();packets.remove(this);closed=true;
        }
        private void destroy(){if(pool!=0){vkDestroyDescriptorPool(device,pool,null);pool=0;}if(uniform!=null){uniform.close();uniform=null;}if(vertex!=null){vertex.close();vertex=null;}}
    }
    private final class Storage implements AutoCloseable {
        long buffer,memory;
        Storage(ByteBuffer data,int usage){
            try(MemoryStack s=MemoryStack.stackPush()){
                LongBuffer out=s.mallocLong(1);check(vkCreateBuffer(device,VkBufferCreateInfo.calloc(s).sType$Default().size(data.remaining()).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE),null,out),"UI buffer");buffer=out.get(0);
                VkMemoryRequirements req=VkMemoryRequirements.calloc(s);vkGetBufferMemoryRequirements(device,buffer,req);
                VkPhysicalDeviceMemoryProperties props=VkPhysicalDeviceMemoryProperties.calloc(s);vkGetPhysicalDeviceMemoryProperties(device.getPhysicalDevice(),props);
                int type=-1,wanted=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
                for(int i=0;i<props.memoryTypeCount();i++)if((req.memoryTypeBits()&(1<<i))!=0&&(props.memoryTypes(i).propertyFlags()&wanted)==wanted){type=i;break;}
                if(type<0)throw new IllegalStateException("Native UI host-coherent memory unavailable");
                check(vkAllocateMemory(device,VkMemoryAllocateInfo.calloc(s).sType$Default().allocationSize(req.size()).memoryTypeIndex(type),null,out),"UI buffer memory");memory=out.get(0);
                check(vkBindBufferMemory(device,buffer,memory,0),"UI bind memory");PointerBuffer mapped=s.mallocPointer(1);
                check(vkMapMemory(device,memory,0,data.remaining(),0,mapped),"UI map data");
                try{MemoryUtil.memByteBuffer(mapped.get(0),data.remaining()).put(data.duplicate());}finally{vkUnmapMemory(device,memory);}
            }catch(Throwable failure){close();throw failure;}
        }
        @Override public void close(){if(buffer!=0){vkDestroyBuffer(device,buffer,null);buffer=0;}if(memory!=0){vkFreeMemory(device,memory,null);memory=0;}}
    }
    @Override public void close(){thread();if(closed)return;if(!packets.isEmpty())throw new IllegalStateException("Native UI pipeline has live packets");destroy();closed=true;}
    private void destroy(){if(pipeline!=0){vkDestroyPipeline(device,pipeline,null);pipeline=0;}if(pipelineLayout!=0){vkDestroyPipelineLayout(device,pipelineLayout,null);pipelineLayout=0;}if(descriptorLayout!=0){vkDestroyDescriptorSetLayout(device,descriptorLayout,null);descriptorLayout=0;}if(fragmentModule!=0){vkDestroyShaderModule(device,fragmentModule,null);fragmentModule=0;}if(vertexModule!=0){vkDestroyShaderModule(device,vertexModule,null);vertexModule=0;}}
    private long module(MemoryStack s,ByteBuffer code){LongBuffer out=s.mallocLong(1);check(vkCreateShaderModule(device,VkShaderModuleCreateInfo.calloc(s).sType$Default().pCode(code),null,out),"UI shader module");return out.get(0);}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native UI owner thread required");}
    private static int components(String type){return switch(type){case "float","int"->1;case "vec2","ivec2"->2;case "vec3","ivec3"->3;case "vec4","ivec4"->4;default->throw new IllegalArgumentException("Unsupported UI attribute type "+type);};}
    private static int format(String type){return switch(type){case "float"->VK_FORMAT_R32_SFLOAT;case "vec2"->VK_FORMAT_R32G32_SFLOAT;case "vec3"->VK_FORMAT_R32G32B32_SFLOAT;case "vec4"->VK_FORMAT_R32G32B32A32_SFLOAT;case "int"->VK_FORMAT_R32_SINT;case "ivec2"->VK_FORMAT_R32G32_SINT;case "ivec3"->VK_FORMAT_R32G32B32_SINT;case "ivec4"->VK_FORMAT_R32G32B32A32_SINT;default->throw new IllegalArgumentException("Unsupported UI attribute type "+type);};}
    private static void check(int result,String operation){if(result!=VK_SUCCESS)throw new IllegalStateException(operation+" failed: "+VkResult.decode(result));}
}
