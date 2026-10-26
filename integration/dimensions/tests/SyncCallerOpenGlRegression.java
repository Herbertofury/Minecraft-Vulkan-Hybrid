import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.reflect.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL15C.*;

/** Frozen production pin/ABI controls and real NVIDIA OpenGL original fence execution. */
public final class SyncCallerOpenGlRegression {
    private static int controls,cases,program,vao;private static Path output;
    private static void check(boolean result){if(!result)throw new AssertionError("Control "+controls+" failed");controls++;}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static ClassNode node(byte[] bytes){ClassNode result=new ClassNode();new ClassReader(bytes).accept(result,0);return result;}
    private static void rejected(Runnable action){boolean failed=false;try{action.run();}catch(IllegalStateException expected){failed=true;}check(failed);}
    private static void pureControls(Path jar)throws Exception{
        Class<?> audit=Class.forName("net.vulkanmod.compat.EssentialSyncCallerAudit");Method translated=audit.getDeclaredMethod("translated",String.class,byte[].class);translated.setAccessible(true);
        try(JarFile input=new JarFile(jar.toFile())){
            for(String name:new String[]{"gg.essential.universal.render.UGpuFenceImplKt","gg.essential.universal.render.UGpuFenceImpl"}){
                String entry=name.replace('.','/')+".class";byte[] raw=input.getInputStream(input.getJarEntry(entry)).readAllBytes();check((boolean)translated.invoke(null,entry,raw));byte[] corrupt=raw.clone();corrupt[corrupt.length-1]^=1;check(!(boolean)translated.invoke(null,entry,corrupt));
                ClassNode original=node(raw),changed=node(raw);check(EssentialSyncCallers.apply(changed)==(name.endsWith("Kt")?3:1));rejected(()->EssentialSyncCallers.apply(changed));
                ClassNode mutation=node(raw);mutation.methods.get(0).instructions.insert(new InsnNode(Opcodes.NOP));rejected(()->EssentialSyncCallers.apply(mutation));ClassNode unknown=node(raw);unknown.name="unreviewed/Fence";rejected(()->EssentialSyncCallers.apply(unknown));
                // Remove only the new native prefixes/error transport, then prove the exact
                // original executable body (including original core/ARB branches) remains.
                for(MethodNode method:changed.methods){
                    if(name.endsWith("Kt")&&Set.of("glFenceSync","glDeleteSync","glClientWaitSync").contains(method.name)){
                        AbstractInsnNode head=method.instructions.getFirst();check(head instanceof MethodInsnNode gate&&gate.owner.equals("net/vulkanmod/compat/UniversalRendererGate"));
                        check(head.getNext() instanceof JumpInsnNode branch&&branch.getOpcode()==Opcodes.IFEQ);LabelNode destination=((JumpInsnNode)head.getNext()).label;
                        while(method.instructions.getFirst()!=destination)method.instructions.remove(method.instructions.getFirst());method.instructions.remove(destination);
                        if(method.instructions.getFirst() instanceof FrameNode)method.instructions.remove(method.instructions.getFirst());
                        MethodNode before=original.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst().orElseThrow();method.maxStack=before.maxStack;
                    }else for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(EssentialSyncCallers.FACADE)&&call.name.equals("glGetError"))call.owner="org/lwjgl/opengl/GL11";
                }
                check(ShaderCallerRemapper.canonicalDigest(changed).equals(ShaderCallerRemapper.canonicalDigest(original)));
            }
            String entry="gg/essential/universal/render/UGpuFence.class";byte[] raw=input.getInputStream(input.getJarEntry(entry)).readAllBytes();check(!(boolean)translated.invoke(null,entry,raw));check(!EssentialSyncCallers.target(entry.substring(0,entry.length()-6)));
        }
    }
    private static int shader(int type,String source){int id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id);check(glGetShaderi(id,GL_COMPILE_STATUS)==1);return id;}
    private static void draw(int red,int green,int blue){glUseProgram(program);glUniform4f(glGetUniformLocation(program,"color"),red,green,blue,1);glBindVertexArray(vao);glDrawArrays(GL_TRIANGLE_FAN,0,4);}
    private static void pixels(String label,int red,int green,int blue)throws Exception{
        ByteBuffer raw=ByteBuffer.allocateDirect(4096);glReadPixels(0,0,32,32,GL_RGBA,GL_UNSIGNED_BYTE,raw);int bad=0;
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){boolean ink=x>=3&&x<14&&y>=8&&y<12;int[] expected=ink?new int[]{red*255,green*255,blue*255,255}:new int[]{0,0,255,255};boolean mismatch=false;for(int n=0;n<4;n++)if(Byte.toUnsignedInt(raw.get((y*32+x)*4+n))!=expected[n])mismatch=true;if(mismatch)bad++;}
        byte[] bytes=new byte[4096];raw.get(0,bytes);Files.write(output.resolve(label+".rgba-bottom-up"),bytes);check(bad==0);check(glGetError()==0);cases++;System.out.println("PIXELS "+label+" pixels=1024 mismatch="+bad+" sha256="+hash(bytes));
    }
    public static void main(String[] args)throws Exception{
        output=Path.of(args[2]);Files.createDirectories(output);check(Path.of(NativeSyncApi.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[0]).toRealPath()));check(hash(Files.readAllBytes(Path.of(args[1]))).equals("1219f4cf3dfa874a9a0c0c45b2d48ada1b9a95a10b77ec75f1df60d3f9a27a06"));pureControls(Path.of(args[1]));
        check(glfwInit());glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(32,32,"MVH sync actual OpenGL control",0,0);check(window!=0);glfwMakeContextCurrent(window);var capabilities=org.lwjgl.opengl.GL.createCapabilities();System.out.println("HARDWARE "+glGetString(GL_VENDOR)+" / "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));check(capabilities.OpenGL32&&capabilities.GL_ARB_sync);
        int framebuffer=glGenFramebuffers(),texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,32,32,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glDisable(GL_SCISSOR_TEST);glClearColor(0,0,1,1);glClear(GL_COLOR_BUFFER_BIT);glViewport(2,8,24,20);glScissor(3,7,11,5);glEnable(GL_SCISSOR_TEST);
        int vertex=shader(GL_VERTEX_SHADER,"#version 330\nlayout(location=0) in vec2 position;void main(){gl_Position=vec4(position,0,1);}"),fragment=shader(GL_FRAGMENT_SHADER,"#version 330\nuniform vec4 color;out vec4 result;void main(){result=color;}");program=glCreateProgram();glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==1);glDeleteShader(vertex);glDeleteShader(fragment);vao=glGenVertexArrays();int buffer=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,1,-1,1,1,-1,1},GL_STATIC_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,false,8,0);
        try(OriginalLoader loader=new OriginalLoader(Path.of(args[1]))){
            Class<?> original=Class.forName("gg.essential.universal.render.UGpuFenceImpl",true,loader);Field id=original.getDeclaredField("glId");id.setAccessible(true);Method await=original.getMethod("awaitCompletion",long.class),close=original.getMethod("close");
            draw(0,1,0);Object first=original.getConstructor().newInstance();long nativeId=(long)id.get(first);check(nativeId!=0&&NativeSyncApi.glIsSync(nativeId));check(NativeSyncApi.glGetSynci(nativeId,37138)==37142);check(NativeSyncApi.glGetSynci(nativeId,37139)==37143);check(NativeSyncApi.glGetSynci(nativeId,37141)==0);
            check(await.invoke(first,0L) instanceof Boolean);glFlush();glFinish();check((boolean)await.invoke(first,0L));check(NativeSyncApi.glGetSynci(nativeId,37140)==37145);NativeSyncApi.glWaitSync(nativeId,0,-1L);check(glGetError()==0);pixels("async-original-fence-green",0,1,0);close.invoke(first);check(id.get(first)==null&&!NativeSyncApi.glIsSync(nativeId));check((boolean)await.invoke(first,0L));close.invoke(first);
            draw(1,0,0);long core=NativeSyncApi.glFenceSync(37143,0);check(org.lwjgl.opengl.ARBSync.glIsSync(core));int waited=org.lwjgl.opengl.ARBSync.glClientWaitSync(core,1,2_000_000_000L);check(waited==37146||waited==37148);org.lwjgl.opengl.ARBSync.glDeleteSync(core);pixels("async-continuation-offset-viewport-red",1,0,0);
            draw(0,1,0);long arb=org.lwjgl.opengl.ARBSync.glFenceSync(37143,0);check(NativeSyncApi.glIsSync(arb));waited=NativeSyncApi.glClientWaitSync(arb,1,2_000_000_000L);check(waited==37146||waited==37148);NativeSyncApi.glDeleteSync(arb);pixels("second-async-continuation-green",0,1,0);
            check(NativeSyncApi.glFenceSync(0,0)==0&&NativeSyncApi.glGetError()==1280);check(NativeSyncApi.glFenceSync(37143,1)==0&&NativeSyncApi.glGetError()==1281);
            boolean zeroRejectedByBinding=false;try{NativeSyncApi.glDeleteSync(0);}catch(NullPointerException expected){zeroRejectedByBinding=true;}
            check(zeroRejectedByBinding&&NativeSyncApi.glGetError()==0);check(loader.changed==4);check(cases==3);
        }finally{glUseProgram(0);glDeleteProgram(program);glDeleteBuffers(buffer);glDeleteVertexArrays(vao);glDeleteFramebuffers(framebuffer);glDeleteTextures(texture);glfwDestroyWindow(window);glfwTerminate();}
        Files.writeString(output.resolve("REPORT.json"),"{\"passed\":true,\"pixel_cases\":3,\"compared_pixels\":3072,\"mismatched_pixels\":0,\"controls\":"+controls+",\"original_core_driver_branch_executed\":true,\"original_arb_branch_preserved\":true,\"actual_driver_core_arb_interop\":true,\"minecraft_or_embeddium_launched\":false}\n");System.out.println("PASS_REAL_OPENGL_SYNC3 controls="+controls);
    }
    private static final class OriginalLoader extends URLClassLoader{
        final JarFile jar;int changed;
        OriginalLoader(Path input)throws Exception{super(new URL[]{input.toUri().toURL()},SyncCallerOpenGlRegression.class.getClassLoader());jar=new JarFile(input.toFile());}
        protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(!name.startsWith("gg.essential.universal.render.UGpuFence"))return super.loadClass(name,resolve);synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;}}
        protected Class<?> findClass(String name)throws ClassNotFoundException{try{var entry=jar.getJarEntry(name.replace('.','/')+".class");if(entry==null)throw new ClassNotFoundException(name);byte[] bytes=jar.getInputStream(entry).readAllBytes();if(EssentialSyncCallers.target(name)){ClassNode node=node(bytes);changed+=EssentialSyncCallers.apply(node);ClassWriter writer=new ClassWriter(0);node.accept(writer);bytes=writer.toByteArray();}return defineClass(name,bytes,0,bytes.length);}catch(Exception error){throw new ClassNotFoundException(name,error);}}
        public void close()throws java.io.IOException{try{jar.close();}finally{super.close();}}
    }
}
