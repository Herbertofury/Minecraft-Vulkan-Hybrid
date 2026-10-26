package mvhpackbench;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.texture.*;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import static org.lwjgl.vulkan.VK10.*;

/** Actual original GAME fence objects, device status, async submission, pixels and retirement. */
final class NativeEssentialSyncSmoke {
    private static final JsonArray controls=new JsonArray();
    private static int baselineAllocated,baselineLive;
    private static long retirementStarted;
    private static Class<?> original;
    private static Object shutdownFence;
    private static Field glId;
    private static void require(String label,boolean condition){
        JsonObject row=new JsonObject();row.addProperty("control",label);row.addProperty("passed",condition);controls.add(row);
        if(!condition)throw new IllegalStateException("Native sync control failed: "+label);
    }
    private static Object invoke(Method method,Object target,Object...args)throws Exception{return OriginalEssentialClipProof.invoke(method,target,args);}
    private static boolean await(Object fence,long timeout)throws Exception{return (boolean)invoke(original.getMethod("awaitCompletion",long.class),fence,timeout);}
    private static void close(Object fence)throws Exception{invoke(original.getMethod("close"),fence);}
    private static long id(Object fence)throws Exception{Object value=glId.get(fence);require("original-fence-nonnull-nonzero",value instanceof Long&&(long)value!=0);return (long)value;}
    private static void originalForgeProof()throws Exception{
        Path root=Minecraft.getInstance().gameDirectory.toPath();int branches=0;
        for(String name:new String[]{"gg.essential.universal.render.UGpuFenceImplKt","gg.essential.universal.render.UGpuFenceImpl"}){
            Class<?> type=Class.forName(name,false,NativeEssentialSyncSmoke.class.getClassLoader());
            require("original-game-loader-"+name,type.getClassLoader().getClass().getName().equals("cpw.mods.modlauncher.TransformingClassLoader")&&net.minecraftforge.fml.loading.FMLLoader.getGameLayer().modules().contains(type.getModule()));
            Path export=root.resolve(".mixin.out/class/"+name.replace('.','/')+".class");require("fresh-automatic-export-"+name,Files.isRegularFile(export)&&Files.getLastModifiedTime(export).toMillis()>=ManagementFactory.getRuntimeMXBean().getStartTime());
            ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(export)).accept(node,0);int facade=0,driver=0,gate=0;
            for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){if(call.owner.equals(EssentialSyncCallers.FACADE))facade++;if(call.owner.startsWith("org/lwjgl/opengl/"))driver++;if(call.owner.equals("net/vulkanmod/compat/UniversalRendererGate")&&call.name.equals("vulkanRendererEnabled"))gate++;}
            boolean wrappers=name.endsWith("Kt");require("exact-native-branches-and-retained-fallback-"+name,facade==(wrappers?3:1)&&gate==(wrappers?3:0)&&driver==(wrappers?7:0));branches+=facade;
        }
        require("automatic-production-fence-branches",branches==4);
        original=Class.forName("gg.essential.universal.render.UGpuFenceImpl",true,NativeEssentialSyncSmoke.class.getClassLoader());glId=original.getDeclaredField("glId");glId.setAccessible(true);
    }
    static void run(JsonArray cases)throws Exception{
        originalForgeProof();baselineAllocated=GlSync.allocatedFenceCount();baselineLive=GlSync.liveFenceCount();require("owned-pilot-fence-baseline",baselineAllocated==0&&baselineLive==0);
        require("invalid-condition-rejected",NativeSyncApi.glFenceSync(0,0)==0&&NativeSyncApi.glGetError()==1280&&NativeSyncApi.glGetError()==0);
        require("invalid-create-flags-rejected",NativeSyncApi.glFenceSync(37143,1)==0&&NativeSyncApi.glGetError()==1281);
        require("invalid-wait-handle-rejected",NativeSyncApi.glClientWaitSync(-5,0,0)==37149&&NativeSyncApi.glGetError()==1281);
        NativeSyncApi.glDeleteSync(0);require("delete-zero-no-error",NativeSyncApi.glGetError()==0);
        Renderer renderer=Renderer.getInstance();RenderPass previous=renderer.getBoundRenderPass();NativeStencilSmoke.State state=new NativeStencilSmoke.State();boolean oldClip=Renderer.isScissorEnabled();int[] oldBox=new int[4];for(int n=0;n<4;n++)oldBox[n]=Renderer.getScissorBox(n);
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());var shader=RenderSystem.getShader();float[] shaderColor=RenderSystem.getShaderColor().clone();var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        VulkanImage color=null;Framebuffer framebuffer=null;Object first=null,second=null,third=null;long workerId=0;
        try{
            color=VulkanImage.builder(32,32).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();framebuffer=Framebuffer.builder(color,null).build();VRenderSystem.setClearColor(0,0,1,1);
            require("native-sync-pass",renderer.beginRendering(RenderPass.builder(framebuffer).build(),framebuffer));
            Renderer.setInvertedViewport(2,4,24,20);RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,32,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);VRenderSystem.disableStencilTest();VRenderSystem.colorMask(true,true,true,true);Renderer.setScissor(3,7,11,5);Renderer.setScissorEnabled(true);Renderer.clearAttachments(16384);quad(0,1,0);
            first=original.getConstructor().newInstance();long firstId=id(first);
            glId.set(first,-999L);boolean originalError=false;
            try{await(first,0);}catch(RuntimeException failure){originalError=failure.getMessage().contains("1281");}finally{glId.set(first,firstId);}
            require("original-wait-failed-error-transport",originalError&&NativeSyncApi.glGetError()==0);
            require("unsubmitted-event-reset",NativeSyncApi.glGetSynci(firstId,37140)==37144);
            require("original-zero-poll-pending",!await(first,0));
            long timed=System.nanoTime();require("original-finite-wait-pending",!await(first,1_000_000));require("finite-timeout-not-shortened",System.nanoTime()-timed>=1_000_000);
            require("invalid-existing-wait-flags",NativeSyncApi.glClientWaitSync(firstId,2,0)==37149&&NativeSyncApi.glGetError()==1281);
            NativeSyncApi.glWaitSync(firstId,0,-1L);require("gpu-wait-recorded-without-error",NativeSyncApi.glGetError()==0);
            close(first);require("name-invalid-immediately-resource-retained",!NativeSyncApi.glIsSync(firstId)&&GlSync.allocatedFenceCount()==baselineAllocated+1);require("original-close-idempotent",await(first,0));close(first);first=null;
            second=original.getConstructor().newInstance();long secondId=id(second);require("second-event-pending",!await(second,0));
            int polled=NativeSyncApi.glClientWaitSync(secondId,1,0);require("flush-zero-poll-status",polled==37147||polled==37148);
            int complete=NativeSyncApi.glClientWaitSync(secondId,0,2_000_000_000L);require("submitted-work-real-completion",complete==37146||complete==37148);require("original-await-signaled",await(second,0));
            check(color,cases,"async-original-fence-green",0,1,0);quad(1,0,0);check(color,cases,"async-continuation-offset-viewport-red",1,0,0);close(second);second=null;
            third=original.getConstructor().newInstance();long thirdId=id(third);require("third-original-event-pending",!await(third,0));
            require("flush-finite-completion",NativeSyncApi.glClientWaitSync(thirdId,1,2_000_000_000L)==37148);quad(0,1,0);check(color,cases,"second-async-continuation-green",0,1,0);close(third);third=null;
            workerId=NativeSyncApi.glFenceSync(37143,0);long waitedId=workerId;AtomicReference<Integer> answer=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread waiter=new Thread(()->{try{answer.set(NativeSyncApi.glClientWaitSync(waitedId,0,2_000_000_000L));}catch(Throwable e){failure.set(e);}},"MVH owned native fence waiter");waiter.start();
            Field liveField=GlSync.class.getDeclaredField("LIVE");liveField.setAccessible(true);Object nativeFence=((ConcurrentHashMap<?,?>)liveField.get(null)).get(workerId);Field waiters=nativeFence.getClass().getDeclaredField("hostWaiters");waiters.setAccessible(true);
            long deadline=System.nanoTime()+1_000_000_000L;boolean acquired=false;
            while(System.nanoTime()<deadline){synchronized(nativeFence){acquired=waiters.getInt(nativeFence)>0;}if(acquired)break;Thread.onSpinWait();}
            require("actual-host-waiter-retained",acquired);NativeSyncApi.glDeleteSync(workerId);workerId=0;renderer.flushForSync();waiter.join(2500);require("deleted-inflight-host-waiter-completes",!waiter.isAlive()&&failure.get()==null&&answer.get()!=null&&answer.get()==37148);
            require("deleted-fences-kept-until-frame-fence",GlSync.liveFenceCount()==baselineLive&&GlSync.allocatedFenceCount()==baselineAllocated+4);
            shutdownFence=original.getConstructor().newInstance();id(shutdownFence);require("live-event-kept-for-owned-teardown",GlSync.liveFenceCount()==baselineLive+1&&GlSync.allocatedFenceCount()==baselineAllocated+5);
            retirementStarted=System.nanoTime();
        }finally{
            if(first!=null)close(first);if(second!=null)close(second);if(third!=null)close(third);if(workerId!=0)NativeSyncApi.glDeleteSync(workerId);
            renderer.endRenderPass();renderer.beginRendering(previous,previous.getFramebuffer());Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());Renderer.setScissorEnabled(oldClip);Renderer.setScissor(oldBox[0],oldBox[1],oldBox[2],oldBox[3]);state.restore();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);if(framebuffer!=null)framebuffer.cleanUp();else if(color!=null)color.free();
        }
    }
    static boolean pending(){return retirementStarted!=0;}
    static boolean finish()throws Exception{
        if(GlSync.allocatedFenceCount()!=baselineAllocated+1){if(System.nanoTime()-retirementStarted>5_000_000_000L)throw new IllegalStateException("Deleted GPU events did not retire after frame fences");return false;}
        require("deleted-events-retired-only-after-frame-completion",GlSync.liveFenceCount()==baselineLive+1&&GlSync.allocatedFenceCount()==baselineAllocated+1);require("normal-frame-submitted-original-fence",await(shutdownFence,0));
        JsonObject report=new JsonObject();report.addProperty("accepted",true);report.addProperty("automatic_forge_game_original_methods_executed",true);report.addProperty("full_essential_features_proven",false);report.addProperty("fps_accepted",false);report.addProperty("native_branches",4);report.addProperty("original_opengl_fallback_calls_retained",7);report.addProperty("pixel_cases",3);report.addProperty("deleted_events_retired",4);report.addProperty("live_event_reserved_for_device_idle_teardown",1);report.add("controls",controls);
        Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-sync-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));retirementStarted=0;return true;
    }
    private static void quad(int red,int green,int blue){BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);v.vertex(0,32,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(32,32,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(32,0,0).color(red*255,green*255,blue*255,255).endVertex();v.vertex(0,0,0).color(red*255,green*255,blue*255,255).endVertex();BufferUploader.drawWithShader(v.end());}
    private static void check(VulkanImage image,JsonArray cases,String label,int red,int green,int blue)throws Exception{
        require("logical-clip-preserved-"+label,Renderer.isScissorEnabled()&&Renderer.getScissorBox(0)==3&&Renderer.getScissorBox(1)==7&&Renderer.getScissorBox(2)==11&&Renderer.getScissorBox(3)==5);ByteBuffer pixels=MemoryUtil.memAlloc(4096);
        try{ImageUtil.downloadTexture(image,MemoryUtil.memAddress(pixels));int bad=0;for(int y=0;y<32;y++)for(int x=0;x<32;x++){boolean ink=x>=3&&x<14&&y>=20&&y<24;int[] expected=ink?new int[]{red*255,green*255,blue*255,255}:new int[]{0,0,255,255};boolean mismatch=false;for(int c=0;c<4;c++)if(Byte.toUnsignedInt(pixels.get((y*32+x)*4+c))!=expected[c])mismatch=true;if(mismatch)bad++;}
            byte[] bytes=new byte[4096];pixels.get(0,bytes);Path path=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-sync-gpu-readback");Files.createDirectories(path);Files.write(path.resolve(label+".rgba"),bytes);JsonObject item=new JsonObject();item.addProperty("case","original-sync-"+label);item.addProperty("matched",bad==0);item.addProperty("compared_pixels",1024);item.addProperty("mismatched_pixels",bad);item.addProperty("channel_tolerance",0);item.addProperty("actual_rgba_sha256",OriginalEssentialClipProof.hash(bytes));cases.add(item);require("native-sync-pixels-"+label,bad==0);
        }finally{MemoryUtil.memFree(pixels);}
    }
}
