package mvhpackbench;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.compat.UniversalRendererGate;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.texture.*;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Marker-confined actual GPU draw/readback diagnostic; accepts no FPS. */
final class NativeStencilSmoke {
    private static boolean ponderRoute,irisReferenceRoute;
    private static final boolean ENABLED=verifyRequest();
    private static long joined,finished;
    private static boolean attempted;
    private static final JsonArray cases=new JsonArray();
    static boolean enabled(){return ENABLED;}
    private static boolean verifyRequest(){
        Path root=Minecraft.getInstance().gameDirectory.toPath(),request=root.resolve("mvh-stencil-purpose-request.json");
        if(!Files.isRegularFile(request))return false;
        try{
            JsonObject marker=JsonParser.parseString(Files.readString(root.resolve("MVH-CUMULATIVE-IDENTITY.json"))).getAsJsonObject();
            JsonObject q=JsonParser.parseString(Files.readString(request)).getAsJsonObject();
            if(marker.size()!=3||!marker.get("task").getAsString().equals("mvh-cumulative-20261005")||!marker.get("id").getAsString().equals("local:b6985be9-ded6-4a96-85cd-5a2548b1d900")||!marker.get("source").getAsString().equals("local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3")||root.getFileName().toString().equalsIgnoreCase("Noxviola")||!(q.size()==2||q.size()==3&&q.has("route")&&Set.of("PONDER_DEFAULT_METHODS","IRIS_REFERENCE_SPIRV").contains(q.get("route").getAsString()))||!q.get("task").getAsString().equals("mvh-native-stencil-purpose-20261005")||!q.get("expected_renderer").getAsString().equals("VULKAN"))throw new IllegalStateException("Wrong owned stencil request");
            ponderRoute=q.has("route")&&q.get("route").getAsString().equals("PONDER_DEFAULT_METHODS");
            irisReferenceRoute=q.has("route")&&q.get("route").getAsString().equals("IRIS_REFERENCE_SPIRV");
            return true;
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    static void tick(Minecraft mc){
        if(mc.level==null||mc.player==null)return;
        if(joined==0)joined=System.nanoTime();
        if(finished!=0&&System.nanoTime()-finished>3_000_000_000L){mc.stop();return;}
        if(!attempted&&System.nanoTime()-joined>60_000_000_000L){attempted=true;report(false,"NoRecordingGuiEvent");}
    }
    static void gui(RenderGuiEvent.Post event){
        if(!ENABLED||attempted||joined==0||System.nanoTime()-joined<3_000_000_000L)return;
        attempted=true;RenderSystem.assertOnRenderThread();
        try{
            if(!UniversalRendererGate.vulkanRendererEnabled()||!Renderer.isRecording())throw new IllegalStateException("Not native recording");
            run();report(cases.asList().stream().allMatch(v->v.getAsJsonObject().get("matched").getAsBoolean()),"");
        }catch(Throwable error){report(false,error.getClass().getName());}
    }
    private static void run()throws Exception {
        Renderer renderer=Renderer.getInstance();RenderPass previous=renderer.getBoundRenderPass();
        if(previous==null)throw new IllegalStateException("No owned frame render pass");
        State state=new State();Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var oldShader=RenderSystem.getShader();float[] shaderColor=RenderSystem.getShaderColor().clone();
        PoseStack model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        VulkanImage color=null,depth=null;Framebuffer framebuffer=null;
        try{
            color=VulkanImage.builder(32,32).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();
            depth=VulkanImage.createDepthImage(VK_FORMAT_D32_SFLOAT_S8_UINT,32,32,VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT,false,true);
            framebuffer=Framebuffer.builder(color,depth).build();RenderPass.Builder builder=RenderPass.builder(framebuffer);builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_CLEAR,VK_ATTACHMENT_STORE_OP_STORE);RenderPass pass=builder.build();
            VRenderSystem.setClearColor(0,0,1,1);VRenderSystem.clearDepth(1);VRenderSystem.clearStencil(0);
            if(!renderer.beginRendering(pass,framebuffer))throw new IllegalStateException("Native offscreen pass rejected");
            Renderer.setInvertedViewport(0,0,32,32);Renderer.setScissor(0,0,32,32);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,32,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            if(ponderRoute){
                RenderSystem.clearColor(0,0,1,1);
                ponderDefaultClip();check(color,"ponder_default_render_clip",true,0,1,0);
            }else{
            VRenderSystem.enableStencilTest();VRenderSystem.stencilMask(0xff);VRenderSystem.stencilFunc(519,3,0xff);VRenderSystem.stencilOp(7680,7680,7681);VRenderSystem.colorMask(false,false,false,false);quad(8,8,24,24,0,1,0);
            VRenderSystem.colorMask(true,true,true,true);VRenderSystem.stencilFunc(514,3,0xff);VRenderSystem.stencilMask(0);quad(0,0,32,32,0,1,0);check(color,"replace_equal_clip",true,0,1,0);
            }
            VRenderSystem.enableStencilTest();VRenderSystem.clearStencil(0xaa);Renderer.clearAttachments(1024|16384);VRenderSystem.stencilMask(0x0f);VRenderSystem.stencilFunc(519,0x55,0xff);VRenderSystem.stencilOp(7680,7680,7681);VRenderSystem.colorMask(false,false,false,false);quad(8,8,24,24,0,1,0);
            VRenderSystem.colorMask(true,true,true,true);VRenderSystem.stencilFunc(514,0xa5,0xff);VRenderSystem.stencilMask(0);quad(0,0,32,32,0,1,0);check(color,"masked_replace_preserves_high_bits",true,0,1,0);
            VRenderSystem.disableStencilTest();quad(0,0,32,32,1,0,0);check(color,"disable_restores_unclipped_draw",false,1,0,0);
            if(irisReferenceRoute)referenceShaderDraw(color);
        }finally{
            renderer.endRenderPass();renderer.beginRendering(previous,previous.getFramebuffer());Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());Renderer.setScissor(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());
            state.restore();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setShader(()->oldShader);RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);
            if(framebuffer!=null)framebuffer.cleanUp();else{if(color!=null)color.free();if(depth!=null)depth.free();}
        }
    }
    private static void referenceShaderDraw(VulkanImage image)throws Exception {
        Path root=Minecraft.getInstance().gameDirectory.toPath();
        byte[] vs=Files.readAllBytes(root.resolve("mvh-iris-reference-vertex.spv")),fs=Files.readAllBytes(root.resolve("mvh-iris-reference-fragment.spv"));
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(vs)).equals("722256123b22ca75cb600249d7991e9d25138ec553099053fbe83f81ad51e51c")||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fs)).equals("f88e3f6183a61d199aa355ddd5a2e325d165dc73f4b8cf7f81ccd949cd31d059"))throw new IllegalStateException("Reference compiler SPIR-V fixture changed");
        ByteBuffer vertex=MemoryUtil.memAlloc(vs.length).put(vs).flip(),fragment=MemoryUtil.memAlloc(fs.length).put(fs).flip();
        net.vulkanmod.vulkan.shader.GraphicsPipeline pipeline=null;
        try{
            var builder=new net.vulkanmod.vulkan.shader.Pipeline.Builder(DefaultVertexFormat.POSITION,"mvh-pinned-iris-reference-gpu-proof");
            builder.setUniforms(new ArrayList<>(),new ArrayList<>());
            builder.setSPIRVs(new net.vulkanmod.vulkan.shader.SPIRVUtils.SPIRV(0,vertex),new net.vulkanmod.vulkan.shader.SPIRVUtils.SPIRV(0,fragment));
            pipeline=builder.createGraphicsPipeline();
            Renderer renderer=Renderer.getInstance();renderer.bindGraphicsPipeline(pipeline);renderer.uploadAndBindUBOs(pipeline);
            BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
            v.vertex(-1,-1,0).endVertex();v.vertex(1,-1,0).endVertex();v.vertex(1,1,0).endVertex();v.vertex(-1,1,0).endVertex();
            var buffer=v.end();
            try{Renderer.getDrawer().draw(buffer.vertexBuffer(),VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION,4);}finally{buffer.release();}
            check(image,"iris_reference_spirv_actual_native_draw",false,1,0,1);
        }finally{
            if(pipeline!=null)pipeline.scheduleCleanUp();MemoryUtil.memFree(vertex);MemoryUtil.memFree(fragment);
        }
    }
    private static void ponderDefaultClip()throws Exception {
        Path jar=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-ponder-stencil-diagnostic.jar");
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        if(!digest.equals("a3239fb968d8c059eecd502c05ca2daf826a33f4e66977530e4ab6fc4e92f5c7"))throw new IllegalStateException("Diagnostic Ponder fixture changed");
        // Component test only: Ponder's complete mod requires the still-untranslated Flywheel.
        // Read only the pinned original interface hierarchy and own helper, never its mod entrypoint.
        try(java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},NativeStencilSmoke.class.getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                if(!Set.of("net.createmod.catnip.gui.element.StencilElement","net.createmod.catnip.gui.element.RenderElement","net.createmod.catnip.gui.element.FadableScreenElement","net.createmod.catnip.gui.element.ScreenElement","mvhpondercompat.NativeStencilBridge","mvhpondercompat.NativeStencilBridge$Original").contains(name))throw new ClassNotFoundException("Outside pinned Ponder diagnostic hierarchy: "+name);
                return super.findClass(name);
            }
        }){
        Class<?> type=Class.forName("net.createmod.catnip.gui.element.StencilElement",true,loader);
        if(type.getClassLoader()!=loader)throw new IllegalStateException("Ponder fixture unexpectedly resolved outside diagnostic loader");
        net.minecraft.client.gui.GuiGraphics graphics=new net.minecraft.client.gui.GuiGraphics(Minecraft.getInstance(),Minecraft.getInstance().renderBuffers().bufferSource());
        Object element=java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
            return switch(method.getName()){
                case "getX","getY","getZ" -> 0.0f;
                case "renderStencil" -> {quad(8,8,24,24,0,1,0);yield null;}
                case "renderElement" -> {quad(0,0,32,32,0,1,0);yield null;}
                default -> throw new IllegalStateException("Unknown Ponder diagnostic method: "+method.getName());
            };
        });
        type.getMethod("render",net.minecraft.client.gui.GuiGraphics.class).invoke(element,graphics);
        Class<?> bridge=Class.forName("mvhpondercompat.NativeStencilBridge",true,loader);long[] counts=(long[])bridge.getMethod("callCounts").invoke(null);
        if(counts[0]!=2||counts[1]!=2||VRenderSystem.stencilTest)throw new IllegalStateException("Original Ponder stencil lifecycle/call count mismatch");
        }
    }
    private static void quad(float l,float t,float r,float b,int red,int green,int blue){
        BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        v.vertex(l,b,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(r,b,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(r,t,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(l,t,0).color(red*255,green*255,blue*255,255).endVertex();BufferUploader.drawWithShader(v.end());
    }
    private static void check(VulkanImage image,String label,boolean clipped,int r,int g,int b)throws Exception{
        ByteBuffer pixels=MemoryUtil.memAlloc(32*32*4);
        try{
            ImageUtil.downloadTexture(image,MemoryUtil.memAddress(pixels));int mismatches=0;JsonArray samples=new JsonArray();
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){
                boolean ink=!clipped||(x>=8&&x<24&&y>=8&&y<24);int[] expected=ink?new int[]{r*255,g*255,b*255,255}:new int[]{0,0,255,255};int offset=(y*32+x)*4;
                for(int c=0;c<4;c++)if(Byte.toUnsignedInt(pixels.get(offset+c))!=expected[c]){mismatches++;break;}
                if((x==0&&y==0)||(x==16&&y==16)){JsonArray sample=new JsonArray();sample.add(x);sample.add(y);for(int c=0;c<4;c++)sample.add(Byte.toUnsignedInt(pixels.get(offset+c)));samples.add(sample);}
            }
            byte[] raw=new byte[pixels.capacity()];pixels.get(0,raw);JsonObject item=new JsonObject();item.addProperty("case",label);item.addProperty("matched",mismatches==0);item.addProperty("compared_pixels",1024);item.addProperty("mismatched_pixels",mismatches);item.addProperty("actual_rgba_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));item.add("actual_samples_xy_rgba",samples);cases.add(item);
        }finally{MemoryUtil.memFree(pixels);}
    }
    private static void report(boolean passed,String error){
        JsonObject out=new JsonObject();out.addProperty("passed",passed);out.addProperty("route",ponderRoute?"PONDER_DEFAULT_METHODS":irisReferenceRoute?"IRIS_REFERENCE_SPIRV":"DIRECT_NATIVE_STATE");out.addProperty("completed",true);out.addProperty("fps_accepted",false);out.addProperty("error_type",error);out.addProperty("scope","Actual native shader draws and RGBA Vulkan image readback: clipping, write mask, disable; optional pinned repaired Iris reference compiler SPIR-V through the actual Hari graphics pipeline. Original full Ponder/Create/shader-pack feature scenes remain separate.");out.add("cases",cases);PackControl.recordRenderer(out);
        try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-stencil-purpose.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}finished=System.nanoTime();System.out.println("[MVH Stencil] GPU draw/readback complete passed="+passed+" cases="+cases.size());
    }
    private static final class State{
        final boolean depthTest=VRenderSystem.depthTest,depthMask=VRenderSystem.depthMask,stencil=VRenderSystem.stencilTest,cull=VRenderSystem.cull,blend=net.vulkanmod.vulkan.shader.PipelineState.blendInfo.enabled;
        final int func=VRenderSystem.stencilFunc,ref=VRenderSystem.stencilRef,compare=VRenderSystem.stencilFuncMask,fail=VRenderSystem.stencilFailOp,depthFail=VRenderSystem.stencilDepthFailOp,pass=VRenderSystem.stencilPassOp,write=VRenderSystem.stencilWriteMask,color=VRenderSystem.colorMask,clear=VRenderSystem.clearStencilValue;
        final float clearDepth=VRenderSystem.clearDepthValue;final float[] clearColor=new float[4];State(){for(int i=0;i<4;i++)clearColor[i]=VRenderSystem.clearColor.get(i);}
        void restore(){VRenderSystem.depthTest=depthTest;VRenderSystem.depthMask=depthMask;VRenderSystem.stencilTest=stencil;VRenderSystem.cull=cull;net.vulkanmod.vulkan.shader.PipelineState.blendInfo.enabled=blend;VRenderSystem.stencilFunc=func;VRenderSystem.stencilRef=ref;VRenderSystem.stencilFuncMask=compare;VRenderSystem.stencilFailOp=fail;VRenderSystem.stencilDepthFailOp=depthFail;VRenderSystem.stencilPassOp=pass;VRenderSystem.stencilWriteMask=write;VRenderSystem.colorMask=color;VRenderSystem.clearStencilValue=clear;VRenderSystem.clearDepthValue=clearDepth;for(int i=0;i<4;i++)VRenderSystem.clearColor.put(i,clearColor[i]);}
    }
}
