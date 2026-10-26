package mvhpackbench;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.*;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import static org.lwjgl.vulkan.VK10.*;

/** Private exact original Physics helper; controlled native GPU pixels, no full-mod/FPS claim. */
final class NativePhysicsShaderSmoke {
    private static final JsonArray controls=new JsonArray();private static int calls,rewritten;
    private static void require(String name,boolean ok){JsonObject r=new JsonObject();r.addProperty("control",name);r.addProperty("passed",ok);controls.add(r);if(!ok)throw new IllegalStateException("Native Physics shader control: "+name);}
    private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    private static final class Originals extends URLClassLoader {
        private final JarFile jar;
        Originals(Path file)throws Exception{super(new URL[]{file.toUri().toURL()},NativePhysicsShaderSmoke.class.getClassLoader());jar=new JarFile(file.toFile());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.equals("net.diebuddies.opengl.Shader"))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){Class<?> found=findLoadedClass(name);if(found==null)found=findClass(name);if(resolve)resolveClass(found);return found;}
        }
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            if(!name.equals("net.diebuddies.opengl.Shader"))return super.findClass(name);
            try {
                byte[] raw=jar.getInputStream(jar.getJarEntry(name.replace('.','/')+".class")).readAllBytes();
                require("exact-private-Physics-Shader-body",hash(raw).equals("be8a6b1ae560c63c312f63e9a90f6df02ab8d27ad0728913ea8bc0a2e405b037"));
                ClassNode node=new ClassNode();new ClassReader(raw).accept(node,0);var changed=ShaderCallerRemapper.apply(node);rewritten=changed.directCalls();require("all-original-GL32-shader-calls-transported",rewritten>=34);
                for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call)require("no-original-shader-GL32-invocation-left",!call.owner.equals("org/lwjgl/opengl/GL32C"));
                ClassWriter writer=new ClassWriter(0);node.accept(writer);byte[] bytes=writer.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
        @Override public void close()throws java.io.IOException{try{jar.close();}finally{super.close();}}
    }
    private static Object invoke(Class<?> type,Object target,String name,Class<?>[] signature,Object...args)throws Exception {
        Method method=type.getDeclaredMethod(name,signature);method.setAccessible(true);calls++;
        try{return method.invoke(target,args);}catch(InvocationTargetException e){throw new IllegalStateException("Original Physics method: "+name,e.getCause());}
    }
    private static Object shell(Class<?> type)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Object result=((sun.misc.Unsafe)f.get(null)).allocateInstance(type);
        for(String name:new String[]{"uniformLocations","attributeLocations"}){Field field=type.getDeclaredField(name);field.setAccessible(true);var map=new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<String>();map.defaultReturnValue(-1);field.set(result,map);}
        return result;
    }
    private static int loc(Class<?> type,Object target,String name)throws Exception{return (int)invoke(type,target,"getUniformLocation",new Class<?>[]{String.class},name);}
    private static void upload(Class<?> type,Object object,String name,int rows,float...values)throws Exception {
        int at=loc(type,object,name);require("active-original-uniform-"+name,at>=0);FloatBuffer data=FloatBuffer.wrap(values);invoke(type,object,"setUniform"+rows,new Class<?>[]{int.class,FloatBuffer.class},at,data);require("original-vector-cursor-"+name,data.position()==0);
    }
    private static void matrix(Class<?> type,Object object,String name,int size,boolean transpose,float...values)throws Exception {
        FloatBuffer data=FloatBuffer.wrap(values);invoke(type,object,"setUniformMatrix"+size,new Class<?>[]{String.class,FloatBuffer.class,boolean.class},name,data,transpose);require("original-matrix-cursor-"+name,data.position()==0);
    }
    static void run(JsonArray cases)throws Exception {
        Path root=Minecraft.getInstance().gameDirectory.toPath();Path file=root.resolve("mvh-physics-original-inputs/physics-original-private-reference.jar");
        JsonObject pins=JsonParser.parseString(Files.readString(root.resolve("mvh-physics-original-inputs/PINS.json"))).getAsJsonObject();require("original-Physics-private-archive-pin",hash(Files.readAllBytes(file)).equals(pins.get("jar_sha256").getAsString()));
        try(Originals loader=new Originals(file)) {
            Class<?> type=loader.loadClass("net.diebuddies.opengl.Shader");Object object=shell(type);
            String vertex="attribute vec3 Position;uniform mat2 rotate2[2];uniform mat3 rotate3;uniform mat4 project4;void main(){vec3 p=rotate3*vec3(Position.xy,1);vec2 q=rotate2[1]*p.xy;gl_Position=project4*vec4(q,0,1);}";
            String fragment="uniform float weights[4];uniform vec2 offsets[2];uniform vec3 palette[2];uniform vec4 tint;uniform int state;uniform float dead;out vec4 resultColor;void main(){resultColor=vec4(weights[3],offsets[1].x,palette[1].g+tint.b,float(state)*tint.a/255.0);}";
            invoke(type,object,"loadVertexShader",new Class<?>[]{String.class,String.class},"private-native-physics.vsh",vertex);invoke(type,object,"loadFragmentShader",new Class<?>[]{String.class,String.class},"private-native-physics.fsh",fragment);
            Field v=type.getDeclaredField("vertex"),f=type.getDeclaredField("fragment");v.setAccessible(true);f.setAccessible(true);invoke(type,object,"linkShader",new Class<?>[]{int[].class},new int[]{v.getInt(object),f.getInt(object)});invoke(type,object,"bind",new Class<?>[]{});invoke(type,object,"getAllUniformLocations",new Class<?>[]{});
            int program=(int)invoke(type,object,"getProgramID",new Class<?>[]{});require("original-link-actual-native-SPIRV",GlProgram.getProgrami(program,0x8B82)==1);require("original-active-uniform-reflection",GlProgram.getProgrami(program,0x8B86)==8 && loc(type,object,"dead")==-1);require("original-array-elements-enumerated",loc(type,object,"weights[3]")==loc(type,object,"weights[0]")+3);
            upload(type,object,"weights[0]",1,0,0,0,.2f);upload(type,object,"offsets[0]",2,0,0,.4f,0);upload(type,object,"palette[0]",3,0,0,0,0,.4f,0);
            invoke(type,object,"setUniform",new Class<?>[]{int.class,float[].class},loc(type,object,"tint"),new float[]{0,0,.1f,1});invoke(type,object,"setUniform1",new Class<?>[]{int.class,int.class},loc(type,object,"state"),255);
            invoke(type,object,"setUniform",new Class<?>[]{int.class,float[].class},loc(type,object,"weights[3]"),new float[]{.2f});
            invoke(type,object,"setUniform",new Class<?>[]{int.class,float[].class},loc(type,object,"offsets[1]"),new float[]{.4f,0});
            invoke(type,object,"setUniform",new Class<?>[]{int.class,float[].class},loc(type,object,"palette[1]"),new float[]{0,.4f,0});
            FloatBuffer tint=FloatBuffer.wrap(new float[]{0,0,.1f,1});invoke(type,object,"setUniform4",new Class<?>[]{String.class,FloatBuffer.class},"tint",tint);require("original-vec4-buffer-cursor",tint.position()==0);
            invoke(type,object,"bindFragData",new Class<?>[]{String.class,int.class},"resultColor",0);
            matrix(type,object,"rotate2[0]",2,false,1,0,0,1,1,0,0,1);matrix(type,object,"rotate3",3,false,1,0,0,0,1,0,0,0,1);matrix(type,object,"project4",4,false,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1);
            pixels(cases,"physics-original-uniform-arrays",0,8,0,8,51,102,128);
            matrix(type,object,"rotate2[0]",2,true,1,0,0,1,0,1,.5f,0);pixels(cases,"physics-original-mat2-transpose",0,8,2,6,51,102,128);
            matrix(type,object,"rotate2[0]",2,false,1,0,0,1,.5f,0,0,.5f);matrix(type,object,"rotate3",3,true,1,0,.5f,0,1,0,0,0,1);pixels(cases,"physics-original-mat3-transpose",3,7,2,6,51,102,128);
            upload(type,object,"weights[2]",1,0,.6f);pixels(cases,"physics-original-partial-array-upload",3,7,2,6,153,102,128);
            invoke(type,object,"destroy",new Class<?>[]{});invoke(type,null,"unbind",new Class<?>[]{});require("original-native-program-destroyed",!GlProgram.isProgram(program));
        }
        JsonObject report=new JsonObject();report.addProperty("accepted",true);report.addProperty("original_shader_method_invocations",calls);report.addProperty("original_call_sites_transported",rewritten);report.addProperty("original_constructor_skipped_test_shell",true);report.addProperty("private_original_helper_not_full_mod",true);report.addProperty("fps_accepted",false);report.add("controls",controls);Files.writeString(root.resolve("mvh-physics-shader-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void pixels(JsonArray cases,String name,int left,int right,int top,int bottom,int red,int green,int blue)throws Exception {
        Renderer renderer=Renderer.getInstance();RenderPass pass=renderer.getBoundRenderPass();
        VulkanImage image=VulkanImage.builder(8,8).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).setLinearFiltering(false).createVulkanImage();
        Framebuffer fb=Framebuffer.builder(image,null).build();RenderPass target=RenderPass.builder(fb).build();ByteBuffer bytes=MemoryUtil.memAlloc(256);
        NativeStencilSmoke.State state=new NativeStencilSmoke.State();var oldBlend=PipelineState.blendInfo;
        try {
            require("original-native-offscreen-pass",renderer.beginRendering(target,fb));Renderer.setInvertedViewport(0,0,8,8);Renderer.setScissor(0,0,8,8);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();VRenderSystem.disableStencilTest();VRenderSystem.colorMask(true,true,true,true);PipelineState.blendInfo=PipelineState.defaultBlendInfo();RenderSystem.clearColor(0,0,0,0);Renderer.clearAttachments(0x4000);
            JsonObject debug=new JsonObject();debug.addProperty("program",GlProgram.currentProgramId());debug.addProperty("uniform_bytes",GlProgram.currentNativeProgram().uniformBytes());JsonObject values=new JsonObject();for(String field:GlProgram.currentNativeProgram().uniformNames())values.add(field,new Gson().toJsonTree(GlProgram.getFloatUniform(field)));debug.add("values",values);debug.addProperty("native_draws_before",NativeUiRenderBridge.nativeDraws());debug.addProperty("depth_test",VRenderSystem.depthTest);debug.addProperty("cull",VRenderSystem.cull);debug.addProperty("color_mask",VRenderSystem.colorMask);debug.addProperty("native_error_before_draw",NativeGlErrors.consume());Path diagnostic=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-physics-gpu-readback");Files.createDirectories(diagnostic);Files.writeString(diagnostic.resolve(name+"-before.json"),new GsonBuilder().setPrettyPrinting().create().toJson(debug));ByteBuffer ubo=GlProgram.currentNativeUniforms();byte[] packed=new byte[ubo.remaining()];ubo.get(packed);Files.write(diagnostic.resolve(name+"-ubo.bin"),packed);
            BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);b.vertex(-1,-1,0).endVertex();b.vertex(1,-1,0).endVertex();b.vertex(1,1,0).endVertex();b.vertex(-1,1,0).endVertex();BufferUploader.drawWithShader(b.end());
            debug.addProperty("native_draws_after",NativeUiRenderBridge.nativeDraws());Files.writeString(diagnostic.resolve(name+"-after.json"),new GsonBuilder().setPrettyPrinting().create().toJson(debug));
            ImageUtil.downloadTexture(image,MemoryUtil.memAddress(bytes));
            int mismatches=0,max=0;for(int y=0;y<8;y++)for(int x=0;x<8;x++){boolean inside=x>=left&&x<right&&y>=top&&y<bottom;int[] expected=inside?new int[]{red,green,blue,255}:new int[4];for(int ch=0;ch<4;ch++){int diff=Math.abs((bytes.get((y*8+x)*4+ch)&255)-expected[ch]);max=Math.max(max,diff);if(diff>1)mismatches++;}}
            byte[] raw=new byte[256];bytes.get(0,raw);Path output=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-physics-gpu-readback");Files.createDirectories(output);Files.write(output.resolve(name+".rgba"),raw);JsonObject result=new JsonObject();result.addProperty("case",name);result.addProperty("matched",mismatches==0);result.addProperty("compared_pixels",64);result.addProperty("mismatched_pixels",mismatches);result.addProperty("max_channel_difference",max);result.addProperty("channel_tolerance",1);result.addProperty("actual_rgba_sha256",hash(raw));cases.add(result);require(name+"-actual-Vulkan-pixels",mismatches==0);
        } finally {
            MemoryUtil.memFree(bytes);PipelineState.blendInfo=oldBlend;state.restore();renderer.beginRendering(pass,pass.getFramebuffer());Renderer.setInvertedViewport(0,0,pass.getFramebuffer().getWidth(),pass.getFramebuffer().getHeight());Renderer.setScissor(0,0,pass.getFramebuffer().getWidth(),pass.getFramebuffer().getHeight());renderer.invalidateRenderState();fb.cleanUp();image.free();
        }
    }
}
