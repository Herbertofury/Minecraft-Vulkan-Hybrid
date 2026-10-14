package mvhpackbench;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.*;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import mvhflywheel.NativeFlywheelBatch;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;
import static mvhflywheel.NativeFlywheelBatch.Kind.*;

/** Real original Create shaft textures/mesh and Flywheel compute/instance ABI; diagnostic FPS excluded. */
final class NativeFlywheelSmoke {
    private static final int WIDTH=192,HEIGHT=128;
    private static Path root;
    private static JsonObject manifest;
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] fixture(String name)throws Exception{
        byte[] b=Files.readAllBytes(root.resolve("mvh-flywheel-fixture").resolve(name));
        if(!sha(b).equals(manifest.getAsJsonObject("outputs").get(name).getAsString()))throw new IllegalStateException("Changed original native fixture: "+name);
        return b;
    }
    static void run(JsonArray cases,boolean culling,boolean ponder)throws Exception {
        root=Minecraft.getInstance().gameDirectory.toPath();
        byte[] m=Files.readAllBytes(root.resolve("mvh-flywheel-fixture/MANIFEST.json"));
        if(!sha(m).equals(culling?"1fe51c8362d768fdfa3edd7cd4267aaaf2c016c56f3a6a3484508f3509080524":"c730c719db116a690f4de931c952d563428f0db783db15911f26555ea963cb39"))throw new IllegalStateException("Wrong pinned original Create/Flywheel fixture");
        manifest=JsonParser.parseString(new String(m,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        Renderer renderer=Renderer.getInstance();RenderPass previous=renderer.getBoundRenderPass();
        if(previous==null)throw new IllegalStateException("No owned pass");
        NativeStencilSmoke.State state=new NativeStencilSmoke.State();
        VulkanImage color=null,depth=null,pyramid=null;VulkanImage[] textures=new VulkanImage[2];Framebuffer fb=null;NativeFlywheelBatch batch=null;
        FloatBuffer projection=MemoryUtil.memAllocFloat(16);
        try{
            renderer.endRenderPass();
            for(int i=0;i<2;i++)textures[i]=texture(i==0?"axis.png":"axis_top.png");
            color=VulkanImage.builder(WIDTH,HEIGHT).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();
            depth=VulkanImage.createDepthImage(VK_FORMAT_D32_SFLOAT_S8_UINT,WIDTH,HEIGHT,VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT,false,true);
            fb=Framebuffer.builder(color,depth).build();RenderPass.Builder builder=RenderPass.builder(fb);builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_CLEAR,VK_ATTACHMENT_STORE_OP_STORE);RenderPass pass=builder.build();
            byte[] vertices=fixture("shaft.vertices"),indices=fixture("shaft.indices");
            batch=new NativeFlywheelBatch(Vulkan.getVkDevice(),fixture("apply.comp.spv"),fixture("transformed.vert.spv"),fixture("shaft.frag.spv"),pass.getId(),color.format,depth.format,
                    new long[]{textures[0].getImageView(),textures[1].getImageView()},new long[]{textures[0].getSampler(),textures[1].getSampler()},vertices.length,indices.length,4*76,4*4,28,36);
            batch.mapped(VERTEX).put(vertices);batch.mapped(INDEX).put(indices);
            batch.mapped(TARGET).asIntBuffer().put(new int[]{0,1,2,3});
            instances(batch,false);
            new Matrix4f().setOrtho(-1.5f,1.5f,-.75f,.75f,-3,3,true).get(projection);
            if(culling){
                pyramid=VulkanImage.builder(2,2).setFormat(VK_FORMAT_R32_SFLOAT).setUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();pyramid(pyramid,1);
                byte[] shader=fixture("cull.comp.spv");var frame=mvhshadercompat.SpirvUniformBlock.reflectStructured(ByteBuffer.wrap(shader),1,0);
                if(frame.bytes()!=864||frame.fields().size()!=46)throw new IllegalStateException("Original frame structured ABI changed");
                batch.configureCulling(shader,pyramid.getImageView(),pyramid.getSampler(),frame.bytes(),8,112);
                batch.mapped(PAGE).asIntBuffer().put(new int[]{0,15});new Matrix4f().get(0,batch.mapped(MATRIX));frame(batch,frame,false);
            }
            byte[] initial=null,one=null;
            int[] counts={3,1,0,3};
            for(int trial=0;trial<counts.length;trial++){
                int count=counts[trial];boolean changed=trial==3;
                if(changed)instances(batch,true);
                model(batch,count);command(batch,99);
                byte[] before=new byte[36];batch.mapped(COMMAND).get(before);
                // Host writes become visible before both draws. The explicit reference uses CPU arguments.
                renderer.endRenderPass();batch.applyInstanceCounts(Renderer.getCommandBuffer());
                begin(renderer,pass,fb);batch.drawExplicit(Renderer.getCommandBuffer(),projection,36,count,0,0,1);
                byte[] reference=read(color);
                renderer.endRenderPass();command(batch,99);batch.applyInstanceCounts(Renderer.getCommandBuffer());
                begin(renderer,pass,fb);batch.drawIndirect(Renderer.getCommandBuffer(),projection,0);
                byte[] actual=read(color);ByteBuffer command=batch.mapped(COMMAND);
                int mismatches=0,ink=0;for(int i=0;i<WIDTH*HEIGHT;i++){
                    int o=i*4;boolean same=true;for(int c=0;c<4;c++)if(actual[o+c]!=reference[o+c])same=false;if(!same)mismatches++;
                    if(Byte.toUnsignedInt(actual[o])!=13||Byte.toUnsignedInt(actual[o+1])!=26||Byte.toUnsignedInt(actual[o+2])!=51)ink++;
                }
                boolean abi=command.getInt(4)==count;for(int i=0;i<36;i++)if((i<4||i>=8)&&command.get(i)!=before[i])abi=false;
                boolean changedPixels=!changed||!Arrays.equals(initial,actual);
                boolean target=count==0?ink==0:ink>80;
                if(trial==0)initial=actual;if(trial==1){one=actual;target&=!Arrays.equals(initial,one);}
                JsonObject item=new JsonObject();item.addProperty("case",changed?"original_shaft_changed_pose_color_light":"original_shaft_indirect_instances_"+count);
                item.addProperty("matched",mismatches==0&&abi&&target&&changedPixels);item.addProperty("compared_pixels",WIDTH*HEIGHT);item.addProperty("mismatched_pixels",mismatches);item.addProperty("non_background_pixels",ink);
                item.addProperty("expected_instances",count);item.addProperty("gpu_generated_instances",command.getInt(4));item.addProperty("non_count_command_bytes_preserved",abi);item.addProperty("first_instance",command.getInt(16));item.addProperty("instance_stride",76);item.addProperty("command_stride",36);item.addProperty("live_input_changes_affect_pixels",changedPixels);
                item.addProperty("actual_rgba_sha256",sha(actual));item.addProperty("explicit_reference_rgba_sha256",sha(reference));item.addProperty("compute_dispatches",batch.dispatches());item.addProperty("indexed_draws",batch.draws());cases.add(item);
                if(trial==0||changed)png(actual,changed?"mvh-flywheel-changed.png":"mvh-flywheel-original.png");
            }
            if(culling)culling(cases,batch,renderer,pass,fb,color,pyramid,projection);
            if(ponder)ponderClipping(cases,batch,renderer,pass,fb,color,projection);
        }finally{
            renderer.endRenderPass();renderer.invalidateRenderState();renderer.beginRendering(previous,previous.getFramebuffer());Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());Renderer.setScissor(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());state.restore();
            if(batch!=null)batch.retire(MemoryManager.getInstance()::addFrameOp);
            if(fb!=null)fb.cleanUp();else{if(color!=null)color.free();if(depth!=null)depth.free();}for(VulkanImage texture:textures)if(texture!=null)texture.free();if(pyramid!=null)pyramid.free();MemoryUtil.memFree(projection);
        }
    }
    private static NativeFlywheelBatch.DrawState currentState(){
        int stencil=net.vulkanmod.vulkan.shader.PipelineState.StencilState.encode(VRenderSystem.stencilTest,VRenderSystem.stencilFunc,VRenderSystem.stencilFailOp,VRenderSystem.stencilPassOp,VRenderSystem.stencilDepthFailOp);
        int depth=net.vulkanmod.vulkan.shader.PipelineState.DepthState.decodeDepthFun(net.vulkanmod.vulkan.shader.PipelineState.DepthState.encodeDepthFun(VRenderSystem.depthFun));
        return new NativeFlywheelBatch.DrawState(stencil,VRenderSystem.stencilFuncMask,VRenderSystem.stencilWriteMask,VRenderSystem.stencilRef,VRenderSystem.depthTest,VRenderSystem.depthMask,depth,VRenderSystem.colorMask);
    }
    /** Original Ponder lifecycle draws native original shaft content; CPU crop is independent. */
    private static void ponderClipping(JsonArray cases,NativeFlywheelBatch batch,Renderer renderer,RenderPass pass,Framebuffer fb,VulkanImage color,FloatBuffer projection)throws Exception{
        Path jar=root.resolve("mvh-ponder-stencil-diagnostic.jar");
        if(!sha(Files.readAllBytes(jar)).equals("a3239fb968d8c059eecd502c05ca2daf826a33f4e66977530e4ab6fc4e92f5c7"))throw new IllegalStateException("Changed Ponder fixture");
        Matrix4f oldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());var oldShader=RenderSystem.getShader();float[] oldColor=RenderSystem.getShaderColor().clone();
        PoseStack modelView=RenderSystem.getModelViewStack();modelView.pushPose();modelView.setIdentity();RenderSystem.applyModelViewMatrix();
        try(java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},NativeFlywheelSmoke.class.getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                if(!Set.of("net.createmod.catnip.gui.element.StencilElement","net.createmod.catnip.gui.element.RenderElement","net.createmod.catnip.gui.element.FadableScreenElement","net.createmod.catnip.gui.element.ScreenElement","mvhpondercompat.NativeStencilBridge","mvhpondercompat.NativeStencilBridge$Original").contains(name))throw new ClassNotFoundException("Outside pinned Ponder hierarchy: "+name);
                return super.findClass(name);
            }
        }){
            Class<?> type=Class.forName("net.createmod.catnip.gui.element.StencilElement",true,loader);
            if(type.getClassLoader()!=loader)throw new IllegalStateException("Ponder fixture escaped isolated loader");
            Class<?> bridge=Class.forName("mvhpondercompat.NativeStencilBridge",true,loader);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,WIDTH,HEIGHT,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);VRenderSystem.disableStencilTest();VRenderSystem.colorMask(true,true,true,true);VRenderSystem.clearStencil(0);
            renderer.endRenderPass();instances(batch,false);batch.mapped(TARGET).asIntBuffer().put(new int[]{0,1,2,3});model(batch,3);command(batch,99);batch.applyInstanceCounts(Renderer.getCommandBuffer());
            begin(renderer,pass,fb);batch.drawIndirect(Renderer.getCommandBuffer(),projection,0,currentState());byte[] original=read(color);
            int[][] rectangles={{48,32,144,96},{16,16,80,112}};
            net.minecraft.client.gui.GuiGraphics gui=new net.minecraft.client.gui.GuiGraphics(Minecraft.getInstance(),Minecraft.getInstance().renderBuffers().bufferSource());
            for(int trial=0;trial<rectangles.length;trial++){
                int[] rectangle=rectangles[trial];renderer.endRenderPass();begin(renderer,pass,fb);renderer.invalidateRenderState();
                Object element=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{type},(proxy,method,args)->{
                    if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
                    return switch(method.getName()){
                        case "getX","getY","getZ" -> 0.0f;
                        case "renderStencil" -> {mask(rectangle);yield null;}
                        case "renderElement" -> {try{batch.drawIndirect(Renderer.getCommandBuffer(),projection,0,currentState());}finally{renderer.invalidateRenderState();}yield null;}
                        default -> throw new IllegalStateException("Unknown original Ponder method: "+method.getName());
                    };
                });
                type.getMethod("render",net.minecraft.client.gui.GuiGraphics.class).invoke(element,gui);
                long[] counts=(long[])bridge.getMethod("callCounts").invoke(null);
                byte[] actual=read(color),expected=original.clone();
                for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)if(x<rectangle[0]||x>=rectangle[2]||y<rectangle[1]||y>=rectangle[3]){
                    int offset=(y*WIDTH+x)*4;expected[offset]=13;expected[offset+1]=26;expected[offset+2]=51;expected[offset+3]=(byte)255;
                }
                int mismatch=mismatches(actual,expected),removed=mismatches(actual,original);
                JsonObject item=new JsonObject();item.addProperty("case","original_ponder_native_shaft_clip_"+(trial+1));item.addProperty("matched",mismatch==0&&removed>80&&counts[0]==2*(trial+1)&&counts[1]==2*(trial+1)&&!VRenderSystem.stencilTest);
                item.addProperty("compared_pixels",WIDTH*HEIGHT);item.addProperty("mismatched_pixels",mismatch);item.addProperty("pixels_removed_by_clip",removed);item.addProperty("original_enable_calls",counts[0]);item.addProperty("original_disable_calls",counts[1]);item.addProperty("stencil_disabled_after_cleanup",!VRenderSystem.stencilTest);item.addProperty("actual_rgba_sha256",sha(actual));item.addProperty("independent_cpu_cropped_reference_rgba_sha256",sha(expected));cases.add(item);png(actual,"mvh-flywheel-ponder-clip-"+(trial+1)+".png");
            }
            renderer.endRenderPass();begin(renderer,pass,fb);batch.drawIndirect(Renderer.getCommandBuffer(),projection,0,currentState());byte[] restored=read(color);int mismatch=mismatches(restored,original);
            JsonObject item=new JsonObject();item.addProperty("case","original_ponder_cleanup_native_shaft_unclipped");item.addProperty("matched",mismatch==0&&!VRenderSystem.stencilTest);item.addProperty("compared_pixels",WIDTH*HEIGHT);item.addProperty("mismatched_pixels",mismatch);item.addProperty("stencil_disabled_after_cleanup",!VRenderSystem.stencilTest);item.addProperty("actual_rgba_sha256",sha(restored));item.addProperty("original_unclipped_reference_rgba_sha256",sha(original));cases.add(item);
        }finally{
            renderer.invalidateRenderState();RenderSystem.setProjectionMatrix(oldProjection,VertexSorting.ORTHOGRAPHIC_Z);modelView.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setShader(()->oldShader);RenderSystem.setShaderColor(oldColor[0],oldColor[1],oldColor[2],oldColor[3]);
        }
    }
    private static int mismatches(byte[] a,byte[] b){int result=0;for(int i=0;i<a.length;i+=4)if(a[i]!=b[i]||a[i+1]!=b[i+1]||a[i+2]!=b[i+2]||a[i+3]!=b[i+3])result++;return result;}
    private static void mask(int[] r){
        BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        v.vertex(r[0],r[3],0).color(255,255,255,255).endVertex();v.vertex(r[2],r[3],0).color(255,255,255,255).endVertex();v.vertex(r[2],r[1],0).color(255,255,255,255).endVertex();v.vertex(r[0],r[1],0).color(255,255,255,255).endVertex();BufferUploader.drawWithShader(v.end());
    }
    private static VulkanImage texture(String name)throws Exception{
        try(NativeImage image=NativeImage.read(new java.io.ByteArrayInputStream(fixture(name)))){
            int w=image.getWidth(),h=image.getHeight();ByteBuffer bytes=MemoryUtil.memAlloc(w*h*4).order(ByteOrder.LITTLE_ENDIAN);
            try{for(int y=0;y<h;y++)for(int x=0;x<w;x++)bytes.putInt(image.getPixelRGBA(x,y));bytes.flip();VulkanImage tex=VulkanImage.builder(w,h).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();tex.uploadSubTextureAsync(0,w,h,0,0,0,0,w,bytes);tex.readOnlyLayout();return tex;}finally{MemoryUtil.memFree(bytes);}
        }
    }
    private static void instances(NativeFlywheelBatch batch,boolean changed){
        ByteBuffer b=batch.mapped(INSTANCE);for(int i=0;i<4;i++){
            int offset=i*76;b.putInt(offset,changed?0xff60b0ff:0xffffffff).putInt(offset+4,0).putInt(offset+8,changed?0x00800080:0x00f000f0);
            float x=i==0?9:(i-2)*.65f;Matrix4f pose=new Matrix4f().translation(x,-.5f,0).rotateY(changed?1.1f:.55f).rotateX(changed?.4f:.2f);pose.get(offset+12,b);
        }
    }
    private static void culling(JsonArray cases,NativeFlywheelBatch batch,Renderer renderer,RenderPass pass,Framebuffer fb,VulkanImage color,VulkanImage pyramid,FloatBuffer projection)throws Exception{
        var frame=mvhshadercompat.SpirvUniformBlock.reflectStructured(ByteBuffer.wrap(fixture("cull.comp.spv")),1,0);instances(batch,false);
        for(int trial=0;trial<3;trial++){
            int expected=trial==0?3:0;renderer.endRenderPass();frame(batch,frame,trial==1);pyramid(pyramid,trial==2?0:1);
            batch.mapped(TARGET).asIntBuffer().put(new int[]{0,1,2,3});model(batch,0);command(batch,99);batch.applyInstanceCounts(Renderer.getCommandBuffer());
            begin(renderer,pass,fb);batch.drawExplicit(Renderer.getCommandBuffer(),projection,36,expected,0,0,1);byte[] reference=read(color);
            renderer.endRenderPass();batch.mapped(TARGET).asIntBuffer().put(new int[]{0,-1,-1,-1});model(batch,0);command(batch,99);
            batch.cullVisibleInstances(Renderer.getCommandBuffer(),1);batch.applyInstanceCounts(Renderer.getCommandBuffer());begin(renderer,pass,fb);batch.drawIndirect(Renderer.getCommandBuffer(),projection,0);byte[] actual=read(color);
            int count=batch.mapped(MODEL).getInt(0),drawCount=batch.mapped(COMMAND).getInt(4),mismatches=0,ink=0;Set<Integer> targets=new HashSet<>();for(int i=0;i<count&&i<3;i++)targets.add(batch.mapped(TARGET).getInt((i+1)*4));
            for(int i=0;i<WIDTH*HEIGHT;i++){int o=i*4;boolean same=true;for(int c=0;c<4;c++)if(actual[o+c]!=reference[o+c])same=false;if(!same)mismatches++;if(Byte.toUnsignedInt(actual[o])!=13||Byte.toUnsignedInt(actual[o+1])!=26||Byte.toUnsignedInt(actual[o+2])!=51)ink++;}
            boolean target=expected==3?targets.equals(Set.of(1,2,3))&&ink>80:targets.isEmpty()&&ink==0;
            JsonObject item=new JsonObject();item.addProperty("case",new String[]{"original_cull_visible_target_compaction","original_cull_frustum_reject","original_cull_sampled_depth_reject"}[trial]);item.addProperty("matched",count==expected&&drawCount==expected&&mismatches==0&&target);item.addProperty("compared_pixels",WIDTH*HEIGHT);item.addProperty("mismatched_pixels",mismatches);item.addProperty("non_background_pixels",ink);item.addProperty("expected_instances",expected);item.addProperty("gpu_culled_instances",count);item.addProperty("gpu_generated_draw_instances",drawCount);item.addProperty("first_instance",1);item.addProperty("frame_uniform_bytes",frame.bytes());item.addProperty("frame_uniform_leaves",frame.fields().size());item.addProperty("exact_expected_compacted_targets",target);item.addProperty("actual_rgba_sha256",sha(actual));item.addProperty("explicit_reference_rgba_sha256",sha(reference));item.addProperty("compute_dispatches",batch.dispatches());item.addProperty("indexed_draws",batch.draws());cases.add(item);
            if(trial==0)png(actual,"mvh-flywheel-culled-visible.png");
        }
    }
    private static void frame(NativeFlywheelBatch batch,mvhshadercompat.SpirvUniformBlock layout,boolean reject){
        ByteBuffer b=batch.mapped(FRAME);layout.writeFloats(b,layout.fieldOrdinal(0,0),1,-1,0,0);layout.writeFloats(b,layout.fieldOrdinal(0,1),0,0,1,-1);layout.writeFloats(b,layout.fieldOrdinal(0,2),0,0,0,0);layout.writeFloats(b,layout.fieldOrdinal(0,3),reject?-100:1.5f,reject?-100:1.5f,reject?-100:.75f,reject?-100:.75f);
        layout.writeFloats(b,layout.fieldOrdinal(0,4),0,0);layout.writeFloats(b,layout.fieldOrdinal(0,5),0,0);layout.writeFloats(b,layout.fieldOrdinal(0,6),1,-1);layout.writeFloats(b,layout.fieldOrdinal(0,7),3,3);
        float[] cull={.1f,100,2/3f,4/3f,2,2};for(int i=0;i<6;i++)layout.writeFloats(b,layout.fieldOrdinal(1,i),cull[i]);layout.writeInts(b,layout.fieldOrdinal(1,6),0);layout.writeInts(b,layout.fieldOrdinal(1,7),0);
        float[] view=new float[16];new Matrix4f().translation(0,0,-4).get(view);layout.writeFloats(b,layout.fieldOrdinal(2),view);
    }
    private static void pyramid(VulkanImage texture,float value){ByteBuffer b=MemoryUtil.memAlloc(16).order(ByteOrder.LITTLE_ENDIAN);try{for(int i=0;i<4;i++)b.putFloat(value);b.flip();texture.uploadSubTextureAsync(0,2,2,0,0,0,0,2,b);texture.readOnlyLayout();}finally{MemoryUtil.memFree(b);}}
    private static void model(NativeFlywheelBatch b,int count){b.mapped(MODEL).putInt(0,count).putInt(4,1).putInt(8,0).putFloat(12,0).putFloat(16,.5f).putFloat(20,0).putFloat(24,1);}
    private static void command(NativeFlywheelBatch b,int count){b.mapped(COMMAND).asIntBuffer().put(new int[]{36,count,0,0,1,0,0,0,0});}
    private static void begin(Renderer r,RenderPass pass,Framebuffer fb){VRenderSystem.setClearColor(13/255f,26/255f,51/255f,1);VRenderSystem.clearDepth(1);if(!r.beginRendering(pass,fb))throw new IllegalStateException("Native batch pass rejected");Renderer.setInvertedViewport(0,0,WIDTH,HEIGHT);Renderer.setScissor(0,0,WIDTH,HEIGHT);}
    private static byte[] read(VulkanImage color){ByteBuffer b=MemoryUtil.memAlloc(WIDTH*HEIGHT*4);try{ImageUtil.downloadTexture(color,MemoryUtil.memAddress(b));byte[] bytes=new byte[b.capacity()];b.get(bytes);return bytes;}finally{MemoryUtil.memFree(b);}}
    private static void png(byte[] pixels,String name)throws Exception{try(NativeImage image=new NativeImage(WIDTH,HEIGHT,false)){ByteBuffer b=ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)image.setPixelRGBA(x,y,b.getInt());image.writeToFile(root.resolve(name));}}
}
