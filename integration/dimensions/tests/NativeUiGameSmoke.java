package mvhpackbench;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Marker-confined real Minecraft native legacy UI-buffer adapter proof, no FPS acceptance. */
final class NativeUiGameSmoke {
    private static final Map<String,String> HASHES=Map.of(
        "000-circle.fsh","259cff0e9e440212db1714c3e28ca39e2d632d9c328a7f4e3275f81a2739f9b7",
        "001-font.fsh","47efc60d7827351f0627c8191cffc5fba9d0c419c336a62702a380ff64c99ade",
        "002-font.vsh","24fb624551cb215701d44750a9a9bc224df429f3fa1832644af2948d1d453952",
        "003-rect.vsh","716c2484e10c915b45f45c3bdf34e7209b490d9d1fbd28885262eb44e49fe01d",
        "004-rounded_rect.fsh","62998adcadedb6287ffbaa94d139c8786388bd5c57b64b288a2fadf243cc5d62");
    private static String shader(String name)throws Exception{Path root=Minecraft.getInstance().gameDirectory.toPath();Path p=root.resolve("mvh-ui-original-inputs").resolve(name);byte[] b=Files.readAllBytes(p);if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).equals(HASHES.get(name)))throw new IllegalStateException("Original Essential UI shader input changed: "+name);return new String(b,java.nio.charset.StandardCharsets.UTF_8);}
    private static boolean arb;
    // Reflective dispatch checks the production caller transport, alongside exact originals.
    // Bootstrap LWJGL ownership is recorded separately and is not claimed as interposed.
    private static final JsonArray apiTrace=new JsonArray();
    private static void inspectApiOwners()throws ReflectiveOperationException,java.io.IOException{
        JsonArray owners=new JsonArray();
        for(String name:new String[]{"org.lwjgl.opengl.GL20C","org.lwjgl.opengl.ARBShaderObjects"}){
            Class<?> type=Class.forName(name,false,NativeUiGameSmoke.class.getClassLoader());JsonObject row=new JsonObject();row.addProperty("class",name);row.addProperty("class_loader",type.getClassLoader().getClass().getName());row.addProperty("class_loader_name",type.getClassLoader().getName());row.addProperty("module",type.getModule().getName());row.addProperty("core_loader",GlProgram.class.getClassLoader().getClass().getName());row.addProperty("module_in_game_layer",net.minecraftforge.fml.loading.FMLLoader.getGameLayer().modules().contains(type.getModule()));
            JsonArray methods=new JsonArray();for(var method:type.getDeclaredMethods())for(var annotation:method.getDeclaredAnnotations())if(annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")){JsonObject item=new JsonObject();item.addProperty("method",method.getName());item.addProperty("mixin",(String)annotation.annotationType().getMethod("mixin").invoke(annotation));methods.add(item);}row.add("merged_methods",methods);owners.add(row);
        }
        Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-ui-api-owners.json"),new GsonBuilder().setPrettyPrinting().create().toJson(owners));
    }
    private static void trace(String phase,String owner,String method,String merged){JsonObject row=new JsonObject();row.addProperty("phase",phase);row.addProperty("class",owner);row.addProperty("method",method);row.addProperty("merged_mixin",merged);apiTrace.add(row);try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-ui-runtime-trace.json"),new GsonBuilder().setPrettyPrinting().create().toJson(apiTrace));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
    private static Object api(boolean extension,String method,Class<?>[] types,Object...arguments){
        String owner="net.vulkanmod.gl.NativeShaderApi";
        try{
            trace("caller_transport_before_dispatch",owner,method,"");
            Object result=NativeShaderApi.class.getMethod(method,types).invoke(null,arguments);
            trace("caller_transport_returned",owner,method,"");return result;
        }catch(java.lang.reflect.InvocationTargetException failure){throw new IllegalStateException("Production shader caller transport failed: "+method,failure.getCause());}catch(ReflectiveOperationException failure){throw new IllegalStateException("Production shader caller ABI unavailable: "+method,failure);}
    }
    private static Object call(boolean extension,String core,String extra,Class<?>[] types,Object...args){return api(extension,extension?extra:core,types,args);}
    private static final Class<?>[] II={int.class,int.class},I={int.class};
    private static int query(boolean extension,int object,int pname,boolean shader){return (int)call(extension,shader?"glGetShaderi":"glGetProgrami","glGetObjectParameteriARB",II,object,pname);}
    private static void use(int p){call(arb,"glUseProgram","glUseProgramObjectARB",I,p);}
    private static void deleteProgram(int p){call(!arb,"glDeleteProgram","glDeleteObjectARB",I,p);}
    private static int compile(String vs,String fs,int iteration){
        arb=iteration==0;
        int v=(int)call(arb,"glCreateShader","glCreateShaderObjectARB",I,GL20.GL_VERTEX_SHADER),f=(int)call(!arb,"glCreateShader","glCreateShaderObjectARB",I,GL20.GL_FRAGMENT_SHADER);
        int p=(int)call(arb,"glCreateProgram","glCreateProgramObjectARB",new Class<?>[]{});
        if(v==f||v==p||f==p)throw new IllegalStateException("Shared real core/ARB object namespace collision");
        call(!arb,"glShaderSource","glShaderSourceARB",new Class<?>[]{int.class,CharSequence.class},v,vs);call(arb,"glShaderSource","glShaderSourceARB",new Class<?>[]{int.class,CharSequence.class},f,fs);
        call(!arb,"glCompileShader","glCompileShaderARB",I,v);call(arb,"glCompileShader","glCompileShaderARB",I,f);
        if(query(!arb,v,GL20.GL_COMPILE_STATUS,true)!=1||query(arb,f,GL20.GL_COMPILE_STATUS,true)!=1)throw new IllegalStateException("Actual mixed native stage compilation failed");
        if(!api(!arb,!arb?"glGetShaderSourceARB":"glGetShaderSource",I,v).equals(vs))throw new IllegalStateException("Actual original shader source query changed");
        call(!arb,"glAttachShader","glAttachObjectARB",II,p,v);call(arb,"glAttachShader","glAttachObjectARB",II,p,f);call(!arb,"glLinkProgram","glLinkProgramARB",I,p);
        if(query(arb,p,GL20.GL_LINK_STATUS,false)!=1)throw new IllegalStateException("Actual mixed native pair link failed");
        call(arb,"glDeleteShader","glDeleteObjectARB",I,v);call(!arb,"glDeleteShader","glDeleteObjectARB",I,f);
        if(query(!arb,v,GL20.GL_DELETE_STATUS,true)!=1||query(arb,f,GL20.GL_DELETE_STATUS,true)!=1)throw new IllegalStateException("Actual attached deleted shader lost");
        call(!arb,"glDetachShader","glDetachObjectARB",II,p,v);call(arb,"glDetachShader","glDetachObjectARB",II,p,f);
        call(arb,"glValidateProgram","glValidateProgramARB",I,p);if(query(!arb,p,GL20.GL_VALIDATE_STATUS,false)!=1)throw new IllegalStateException("Actual mixed native pipeline validation failed");
        return p;
    }
    private static int location(int p,String name){ByteBuffer bytes=ByteBuffer.allocateDirect(name.length()+4);bytes.position(2);bytes.put(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).put((byte)0);bytes.flip().position(2);int cursor=bytes.position();int loc=(int)call(!arb,"glGetUniformLocation","glGetUniformLocationARB",new Class<?>[]{int.class,ByteBuffer.class},p,bytes);int textLocation=(int)call(arb,"glGetUniformLocation","glGetUniformLocationARB",new Class<?>[]{int.class,CharSequence.class},p,name);if(loc!=textLocation)throw new IllegalStateException("Mixed native byte/string locations disagree");if((loc<0 && !(loc==-1 && name.equals("subpixel_amount") && GlProgram.nativeProgram(p).uniform(name)==null))||bytes.position()!=cursor)throw new IllegalStateException("Original uniform name/cursor mismatch: "+name);return loc;}
    private static void uniform(int p,String name,float...values){int loc=location(p,name);Class<?>[] types=new Class<?>[values.length+1];Object[] args=new Object[values.length+1];types[0]=int.class;args[0]=loc;for(int n=0;n<values.length;n++){types[n+1]=float.class;args[n+1]=values[n];}call(arb,"glUniform"+values.length+"f","glUniform"+values.length+"fARB",types,args);}
    private static void draw(String kind){BufferBuilder v=Tesselator.getInstance().getBuilder();boolean font=kind.equals("font");v.begin(VertexFormat.Mode.QUADS,font?DefaultVertexFormat.POSITION_TEX:DefaultVertexFormat.POSITION_COLOR);int[][] xy={{0,32},{32,32},{32,0},{0,0}};for(int[] point:xy){var b=v.vertex(point[0],point[1],0);if(font)b.uv(point[0]/32f,point[1]/32f);else b.color(64,128,255,204);b.endVertex();}BufferUploader.drawWithShader(v.end());}
    static void run(JsonArray cases)throws Exception{
        inspectApiOwners();OriginalEssentialForgeApplyProof.run();
        Renderer renderer=Renderer.getInstance();RenderPass previous=renderer.getBoundRenderPass();if(previous==null||!Renderer.isRecording())throw new IllegalStateException("Native frame required");
        NativeStencilSmoke.State state=new NativeStencilSmoke.State();var oldBlend=PipelineState.blendInfo;var oldShader=RenderSystem.getShader();Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();var oldTexture=VTextureSelector.getImage(0);VulkanImage image=null,white=null;Framebuffer fb=null;
        try(OriginalEssentialShaderProof original=new OriginalEssentialShaderProof()){
            image=VulkanImage.builder(32,32).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();fb=Framebuffer.builder(image,null).build();RenderPass pass=RenderPass.builder(fb).build();
            if(!renderer.beginRendering(pass,fb))throw new IllegalStateException("Native UI offscreen pass rejected");Renderer.setInvertedViewport(0,0,32,32);Renderer.setScissor(0,0,32,32);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,32,32,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();VRenderSystem.disableStencilTest();VRenderSystem.colorMask(true,true,true,true);PipelineState.blendInfo=PipelineState.defaultBlendInfo();
            white=VulkanImage.createWhiteTexture();VTextureSelector.bindTexture(0,white);
            long before=NativeUiRenderBridge.nativeDraws();
            for(String kind:new String[]{"circle","rounded_rect","font"}){
                String vs=shader(kind.equals("font")?"002-font.vsh":"003-rect.vsh"),fs=shader(kind.equals("circle")?"000-circle.fsh":kind.equals("font")?"001-font.fsh":"004-rounded_rect.fsh");
                for(int iteration=0;iteration<2;iteration++){
                    int transportControl=compile(vs,fs,iteration);deleteProgram(transportControl);
                    int program=original.compile(kind);
                    try{
                        if(kind.equals("font")){original.uniform("fgColor",0.1f,0.7f,0.2f,1);uniform(program,"fgColor",0.1f,0.7f,0.2f,1);original.uniform("doffset",0.1f);uniform(program,"doffset",0.1f);original.uniform("hint_amount",0.5f);uniform(program,"hint_amount",0.5f);original.uniform("subpixel_amount",0);uniform(program,"subpixel_amount",0);original.uniform("sdf_texel",1f/32,1f/32);uniform(program,"sdf_texel",1f/32,1f/32);original.integer("msdf",0);call(arb,"glUniform1i","glUniform1iARB",II,location(program,"msdf"),0);}
                        else{original.uniform("u_Radius",iteration==0?8:4);uniform(program,"u_Radius",iteration==0?8:4);if(kind.equals("circle")){original.uniform("u_CenterPos",16,16);uniform(program,"u_CenterPos",16,16);}else{original.uniform("u_InnerRect",8,8,24,24);uniform(program,"u_InnerRect",8,8,24,24);}}
                        deleteProgram(program);if(GlProgram.currentProgramId()!=program||query(!arb,program,GL20.GL_DELETE_STATUS,false)!=1)throw new IllegalStateException("Actual current program deleted before last native draw");
                        VRenderSystem.setClearColor(0,0,0,0);Renderer.clearAttachments(16384);draw(kind);check(image,cases,kind,iteration==0?8:4,iteration);
                    }finally{original.unbind();deleteProgram(program);if(GlProgram.isProgram(program))throw new IllegalStateException("Deleted original UI program survived unbind");}
                }
            }
            original.complete();
            if(NativeUiRenderBridge.nativeDraws()-before!=6)throw new IllegalStateException("Actual buffer adapter draw count mismatch");
            // After unbinding/deleting the custom programs, ordinary native drawing must work.
            RenderSystem.disableBlend();VRenderSystem.setClearColor(0,0,0,0);Renderer.clearAttachments(16384);
            BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);v.vertex(0,32,0).color(255,0,0,255).endVertex();v.vertex(32,32,0).color(255,0,0,255).endVertex();v.vertex(32,0,0).color(255,0,0,255).endVertex();v.vertex(0,0,0).color(255,0,0,255).endVertex();BufferUploader.drawWithShader(v.end());check(image,cases,"vanilla-restored",0,0);
        }finally{
            GlProgram.useProgram(0);renderer.endRenderPass();renderer.beginRendering(previous,previous.getFramebuffer());Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());Renderer.setScissor(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());renderer.invalidateRenderState();VTextureSelector.bindTexture(0,oldTexture);PipelineState.blendInfo=oldBlend;state.restore();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);model.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setShader(()->oldShader);
            if(fb!=null)fb.cleanUp();if(image!=null)image.free();if(white!=null)white.free();
        }
        NativeElementaSmoke.run(cases);
        NativePixelBufferSmoke.run(cases);
        NativePhysicsShaderSmoke.run(cases);
        NativePhysicsBufferSmoke.run(cases);
        NativePhysicsVertexSmoke.run(cases);
        NativePhysicsVaoSmoke.run(cases);
        try{NativeEssentialBufferSmoke.run(cases);}catch(Throwable failure){
            JsonObject diagnostic=new JsonObject();JsonArray chain=new JsonArray();for(Throwable cause=failure;cause!=null;cause=cause.getCause()){JsonObject item=new JsonObject();item.addProperty("type",cause.getClass().getName());String message=cause.getMessage();if(message!=null&&message.matches("[a-zA-Z0-9_.$/]+"))item.addProperty("missing_class",message);JsonArray stack=new JsonArray();for(var frame:cause.getStackTrace()){JsonObject row=new JsonObject();row.addProperty("class",frame.getClassName());row.addProperty("method",frame.getMethodName());row.addProperty("line",frame.getLineNumber());stack.add(row);}item.add("stack",stack);chain.add(item);}diagnostic.add("causes",chain);Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-buffer-error.json"),new GsonBuilder().setPrettyPrinting().create().toJson(diagnostic));throw failure;
        }
        try{NativeEssentialImageSmoke.run(cases);}catch(Throwable failure){JsonObject diagnostic=new JsonObject();JsonArray chain=new JsonArray();for(Throwable cause=failure;cause!=null;cause=cause.getCause()){JsonObject item=new JsonObject();item.addProperty("type",cause.getClass().getName());String message=cause.getMessage();if(message!=null&&message.matches("[a-zA-Z0-9_.$/]+"))item.addProperty("missing_class",message);JsonArray stack=new JsonArray();for(var frame:cause.getStackTrace()){JsonObject row=new JsonObject();row.addProperty("class",frame.getClassName());row.addProperty("method",frame.getMethodName());row.addProperty("line",frame.getLineNumber());stack.add(row);}item.add("stack",stack);chain.add(item);}diagnostic.add("causes",chain);Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-image-error.json"),new GsonBuilder().setPrettyPrinting().create().toJson(diagnostic));throw failure;}
        try{NativeEssentialFramebufferSmoke.run(cases);}catch(Throwable failure){JsonObject diagnostic=new JsonObject();JsonArray chain=new JsonArray();for(Throwable cause=failure;cause!=null;cause=cause.getCause()){JsonObject item=new JsonObject();item.addProperty("type",cause.getClass().getName());String message=cause.getMessage();if(message!=null&&message.matches("[a-zA-Z0-9_.$/]+"))item.addProperty("missing_class",message);JsonArray stack=new JsonArray();for(var frame:cause.getStackTrace()){JsonObject row=new JsonObject();row.addProperty("class",frame.getClassName());row.addProperty("method",frame.getMethodName());row.addProperty("line",frame.getLineNumber());stack.add(row);}item.add("stack",stack);chain.add(item);}diagnostic.add("causes",chain);Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-framebuffer-error.json"),new GsonBuilder().setPrettyPrinting().create().toJson(diagnostic));throw failure;}
        try{NativeEssentialTransferSmoke.run(cases);}catch(Throwable failure){JsonObject diagnostic=new JsonObject();JsonArray chain=new JsonArray();for(Throwable cause=failure;cause!=null;cause=cause.getCause()){JsonObject item=new JsonObject();item.addProperty("type",cause.getClass().getName());String message=cause.getMessage();if(message!=null&&message.matches("[a-zA-Z0-9_.$/]+"))item.addProperty("missing_class",message);JsonArray stack=new JsonArray();for(var frame:cause.getStackTrace()){JsonObject row=new JsonObject();row.addProperty("class",frame.getClassName());row.addProperty("method",frame.getMethodName());row.addProperty("line",frame.getLineNumber());stack.add(row);}item.add("stack",stack);chain.add(item);}diagnostic.add("causes",chain);Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-transfer-error.json"),new GsonBuilder().setPrettyPrinting().create().toJson(diagnostic));throw failure;}
        NativeEssentialClipSmoke.run(cases);
        NativeEssentialSyncSmoke.run(cases);
        NativeEssentialViewportSmoke.run(cases);
    }
    private static double smooth(double lo,double hi,double x){double t=Math.max(0,Math.min(1,(x-lo)/(hi-lo)));return t*t*(3-2*t);}
    private static void check(VulkanImage image,JsonArray cases,String kind,float radius,int iteration)throws Exception{
        ByteBuffer pixels=MemoryUtil.memAlloc(4096);try{ImageUtil.downloadTexture(image,MemoryUtil.memAddress(pixels));int bad=0,max=0;
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){double alpha;if(kind.equals("circle"))alpha=(1-smooth(radius-1,radius,Math.hypot(x+0.5-16,y+0.5-16)))*204/255.0;else if(kind.equals("rounded_rect")){double dx=Math.max(x+0.5-24,8-x-0.5),dy=Math.max(y+0.5-24,8-y-0.5);alpha=(1-smooth(0,1,Math.hypot(Math.max(0,dx),Math.max(0,dy))-radius))*204/255.0;}else alpha=1;
                double[] rgb=kind.equals("font")?new double[]{0.1,0.7,0.2}:kind.equals("vanilla-restored")?new double[]{1,0,0}:new double[]{64/255.0,128/255.0,1};boolean mismatch=false;for(int c=0;c<4;c++){int expected=(int)Math.round(255*alpha*(c==3?1:rgb[c]));int error=Math.abs(Byte.toUnsignedInt(pixels.get((y*32+x)*4+c))-expected);max=Math.max(max,error);if(error>1)mismatch=true;}if(mismatch)bad++;}
            byte[] raw=new byte[4096];pixels.get(0,raw);JsonObject item=new JsonObject();item.addProperty("case","original-ui-"+kind+"-"+iteration);item.addProperty("matched",bad==0);item.addProperty("compared_pixels",1024);item.addProperty("mismatched_pixels",bad);item.addProperty("max_channel_difference",max);item.addProperty("channel_tolerance",1);item.addProperty("actual_rgba_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));item.addProperty("actual_original_essential_executable",!kind.equals("vanilla-restored"));item.addProperty("mixed_core_arb_transport_compilation_control",!kind.equals("vanilla-restored"));item.addProperty("draw_while_program_delete_pending",!kind.equals("vanilla-restored"));cases.add(item);
            Path directory=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-ui-gpu-readback");Files.createDirectories(directory);Files.write(directory.resolve(kind+"-"+iteration+".rgba"),raw);
            if(bad!=0)throw new IllegalStateException("Original UI shader pixels mismatch "+kind+" count="+bad+" max="+max);
        }finally{MemoryUtil.memFree(pixels);}
    }
}
