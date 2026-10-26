import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.security.MessageDigest;
import java.lang.reflect.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL15C.*;

/** Actual OpenGL driver path of the packaged clipping facade; no Minecraft fallback claim. */
public final class ClipCallerOpenGlRegression {
    private static int program,vao,controls,cases;private static Path output;
    private static void check(boolean result){if(!result)throw new AssertionError("Control "+controls+" failed");controls++;}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static Object invoke(Object object,String name)throws Exception{return object.getClass().getMethod(name).invoke(object);}
    private static void activate(Object state)throws Exception{invoke(state,"activate");}
    private static void capture(Class<?> type,boolean enabled,int x,int y,int width,int height)throws Exception{
        Object companion=type.getField("Companion").get(null),state=invoke(companion,"active");check((boolean)invoke(state,"getEnabled")==enabled);int[] expected={x,y,width,height};String[] getter={"getX","getY","getWidth","getHeight"};for(int n=0;n<4;n++)check((int)invoke(state,getter[n])==expected[n]);
    }
    private static Object state(Class<?> type,boolean enabled,int x,int y,int width,int height)throws Exception{return type.getConstructor(boolean.class,int.class,int.class,int.class,int.class).newInstance(enabled,x,y,width,height);}
    private static void clear(){boolean enabled=glIsEnabled(GL_SCISSOR_TEST);glDisable(GL_SCISSOR_TEST);glClearColor(0,0,1,1);glClear(GL_COLOR_BUFFER_BIT);if(enabled)glEnable(GL_SCISSOR_TEST);}
    private static void draw(int red,int green,int blue){glUseProgram(program);glUniform4f(glGetUniformLocation(program,"color"),red,green,blue,1);glBindVertexArray(vao);glDrawArrays(GL_TRIANGLE_FAN,0,4);}
    private static void pixels(String label,int left,int bottom,int width,int height,int red,int green,int blue)throws Exception{
        ByteBuffer raw=ByteBuffer.allocateDirect(4096);glReadPixels(0,0,32,32,GL_RGBA,GL_UNSIGNED_BYTE,raw);int bad=0;
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){boolean ink=x>=left&&x<left+width&&y>=bottom&&y<bottom+height;int[] expected=ink?new int[]{red*255,green*255,blue*255,255}:new int[]{0,0,255,255};boolean mismatch=false;for(int n=0;n<4;n++)if(Byte.toUnsignedInt(raw.get((y*32+x)*4+n))!=expected[n])mismatch=true;if(mismatch)bad++;}
        byte[] bytes=new byte[4096];raw.get(0,bytes);Files.write(output.resolve(label+".rgba-bottom-up"),bytes);check(bad==0);check(glGetError()==0);cases++;System.out.println("PIXELS "+label+" pixels=1024 mismatch="+bad+" sha256="+hash(bytes));
    }
    private static int shader(int type,String source){int id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id);check(glGetShaderi(id,GL_COMPILE_STATUS)==1);return id;}
    public static void main(String[] args)throws Exception{
        output=Path.of(args[3]);Files.createDirectories(output);check(Path.of(NativeClipApi.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(Path.of(args[0]).toRealPath()));
        check(glfwInit());glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(32,32,"Private clipping driver proof",0,0);check(window!=0);glfwMakeContextCurrent(window);org.lwjgl.opengl.GL.createCapabilities();System.out.println("OPENGL_DRIVER "+glGetString(GL_RENDERER)+" "+glGetString(GL_VERSION));
        int texture=glGenTextures(),framebuffer=glGenFramebuffers(),depth=glGenRenderbuffers();glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,32,32,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH24_STENCIL8,32,32);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_STENCIL_ATTACHMENT,GL_RENDERBUFFER,depth);check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE);glViewport(0,0,32,32);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        int vertex=shader(GL_VERTEX_SHADER,"#version 330\nlayout(location=0) in vec2 position;void main(){gl_Position=vec4(position,0,1);}"),fragment=shader(GL_FRAGMENT_SHADER,"#version 330\nuniform vec4 color;out vec4 result;void main(){result=color;}");program=glCreateProgram();glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==1);glDeleteShader(vertex);glDeleteShader(fragment);
        vao=glGenVertexArrays();int buffer=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,1,-1,1,1,-1,1},GL_STATIC_DRAW);glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,false,8,0);
        try(OriginalLoader loader=new OriginalLoader(Path.of(args[1]),Path.of(args[2]))){
            for(String name:new String[]{"gg.essential.universal.render.ScissorState","gg.essential.util.ScissorState"}){
                Class<?> type=Class.forName(name,true,loader);String tag=name.contains("universal")?"universal":"legacy";
                activate(state(type,true,3,7,11,5));capture(type,true,3,7,11,5);clear();draw(0,1,0);pixels(tag+"-asymmetric",3,7,11,5,0,1,0);capture(type,true,3,7,11,5);draw(1,0,0);pixels(tag+"-readback-resumes-clip",3,7,11,5,1,0,0);
                activate(state(type,false,3,7,11,5));capture(type,false,3,7,11,5);clear();draw(0,1,0);pixels(tag+"-disabled-stored-box",0,0,32,32,0,1,0);
                NativeClipApi.glEnable(GL_SCISSOR_TEST);capture(type,true,3,7,11,5);clear();draw(1,0,0);pixels(tag+"-reenabled-stored-box",3,7,11,5,1,0,0);
                Object coerced=type.getMethod("coerceIn",int.class,int.class,int.class,int.class).invoke(state(type,true,-3,-4,14,13),2,3,6,4);activate(coerced);capture(type,true,2,3,6,4);clear();draw(0,1,0);pixels(tag+"-coerced",2,3,6,4,0,1,0);
                activate(state(type,true,-5,-7,8,10));capture(type,true,-5,-7,8,10);clear();draw(0,1,0);pixels(tag+"-negative-origin",0,0,3,3,0,1,0);
                activate(state(type,true,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE));capture(type,true,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);clear();draw(0,1,0);pixels(tag+"-overflow-outside",0,0,0,0,0,1,0);
                activate(state(type,true,3,7,0,5));capture(type,true,3,7,0,5);clear();draw(0,1,0);pixels(tag+"-zero-extent",0,0,0,0,0,1,0);
            }
            check(loader.rewritten==10);
            // Real-driver stencil control has the same original GL operations; the actual
            // StencilEffect object itself is executed only in the separate Minecraft proof.
            NativeClipApi.glDisable(GL_SCISSOR_TEST);clear();glClearStencil(0);glStencilMask(0xff);glClear(GL_STENCIL_BUFFER_BIT);NativeClipApi.glEnable(GL_STENCIL_TEST);NativeClipApi.glStencilFunc(GL_ALWAYS,2,-1);NativeClipApi.glStencilOp(GL_REPLACE,GL_REPLACE,GL_REPLACE);glColorMask(false,false,false,false);NativeClipApi.glEnable(GL_SCISSOR_TEST);NativeClipApi.glScissor(8,8,16,16);draw(0,1,0);NativeClipApi.glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);NativeClipApi.glStencilFunc(GL_EQUAL,2,-1);NativeClipApi.glStencilOp(GL_KEEP,GL_KEEP,GL_KEEP);draw(0,1,0);pixels("stencil-child-clip",8,8,16,16,0,1,0);draw(1,0,0);pixels("stencil-readback-resumes",8,8,16,16,1,0,0);NativeClipApi.glDisable(GL_STENCIL_TEST);draw(0,1,0);pixels("stencil-disabled-full",0,0,32,32,0,1,0);
            IntBuffer result=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asIntBuffer();for(int n=0;n<8;n++)result.put(n,12345);result.position(2).limit(7);NativeClipApi.glGetIntegerv(GL_SCISSOR_BOX,result);check(result.position()==2&&result.limit()==7&&result.get(0)==12345&&result.get(1)==12345&&result.get(6)==12345);check(glGetError()==0);check(cases==19);
        }finally{glUseProgram(0);glDeleteProgram(program);glDeleteBuffers(buffer);glDeleteVertexArrays(vao);glDeleteRenderbuffers(depth);glDeleteFramebuffers(framebuffer);glDeleteTextures(texture);glfwDestroyWindow(window);glfwTerminate();}
        Files.writeString(output.resolve("REPORT.json"),"{\"passed\":true,\"pixel_cases\":"+cases+",\"compared_pixels\":19456,\"mismatched_pixels\":0,\"controls\":"+controls+",\"actual_original_scissor_classes\":4,\"original_stencil_object_executed\":false,\"minecraft_or_embeddium_launched\":false}\n");System.out.println("PASS_REAL_OPENGL_CLIP19 controls="+controls);
    }
    private static final class OriginalLoader extends URLClassLoader{
        final JarFile jar,kotlin;int rewritten;
        OriginalLoader(Path input,Path stdlib)throws Exception{super(new URL[]{input.toUri().toURL(),stdlib.toUri().toURL()},ClipCallerOpenGlRegression.class.getClassLoader());check(hash(Files.readAllBytes(input)).equals("1219f4cf3dfa874a9a0c0c45b2d48ada1b9a95a10b77ec75f1df60d3f9a27a06"));check(hash(Files.readAllBytes(stdlib)).equals("887587c91713250ad52fe14ad9166d042c33835049890e9437f355ffc5a195b1"));jar=new JarFile(input.toFile());kotlin=new JarFile(stdlib.toFile());}
        protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(!(name.startsWith("gg.essential.universal.render.ScissorState")||name.startsWith("gg.essential.util.ScissorState")||name.startsWith("kotlin.")))return super.loadClass(name,resolve);synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;}}
        protected Class<?> findClass(String name)throws ClassNotFoundException{try{JarFile owner=name.startsWith("kotlin.")?kotlin:jar;var entry=owner.getJarEntry(name.replace('.','/')+".class");if(entry==null)throw new ClassNotFoundException(name);byte[] bytes=owner.getInputStream(entry).readAllBytes();if(EssentialClipCallers.target(name)){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);rewritten+=EssentialClipCallers.apply(node);ClassWriter writer=new ClassWriter(0);node.accept(writer);bytes=writer.toByteArray();}return defineClass(name,bytes,0,bytes.length);}catch(Exception error){throw new ClassNotFoundException(name,error);}}
        public void close()throws java.io.IOException{try{jar.close();}finally{try{kotlin.close();}finally{super.close();}}}
    }
}
