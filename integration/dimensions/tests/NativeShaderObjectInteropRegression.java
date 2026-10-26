import net.vulkanmod.gl.GlProgram;
import net.vulkanmod.gl.GlShaderStrings;
import net.vulkanmod.mixin.compatibility.gl.ARBShaderObjectsM;
import net.vulkanmod.mixin.compatibility.gl.GL20M;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.ARBShaderObjects;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class NativeShaderObjectInteropRegression {
    static int checks;
    static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static String read(ByteBuffer b,int count) { byte[] bytes=new byte[count];b.duplicate().get(bytes);return new String(bytes,StandardCharsets.UTF_8); }
    static byte peek(ByteBuffer b,int index) { return b.duplicate().clear().get(index); }
    static ByteBuffer name(String value) { ByteBuffer b=ByteBuffer.allocate(value.length()+7);b.position(2);b.put(value.getBytes(StandardCharsets.UTF_8)).put((byte)0).put((byte)42);b.limit(b.position());b.position(2);return b; }
    static ByteBuffer output(int extent) { ByteBuffer b=ByteBuffer.allocate(extent+4);for(int i=0;i<b.capacity();i++)b.put(i,(byte)91);b.position(2).limit(2+extent);return b; }
    public static void main(String[] args) throws Exception {
        Path inputs=Path.of(args[0]);String vertex=Files.readString(inputs.resolve("003-rect.vsh")),fragment=Files.readString(inputs.resolve("000-circle.fsh"));
        List<Object> retired=new ArrayList<>();GlProgram.setNativeProgramRetirer(retired::add);
        int v=ARBShaderObjectsM.glCreateShaderObjectARB(GL20.GL_VERTEX_SHADER);
        int f=GL20M.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        int p=ARBShaderObjectsM.glCreateProgramObjectARB();int q=GL20M.glCreateProgram();
        require(new HashSet<>(List.of(v,f,p,q)).size()==4,"all core/ARB shader and program names share one namespace");
        GL20M.glShaderSource(v,vertex);ARBShaderObjectsM.glShaderSourceARB(f,fragment);
        ARBShaderObjectsM.glCompileShaderARB(v);GL20M.glCompileShader(f);
        require(GL20M.glGetShaderi(v,GL20.GL_COMPILE_STATUS)==1&&ARBShaderObjectsM.glGetObjectParameteriARB(f,ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB)==1,"mixed API stages really compile");
        require(ARBShaderObjectsM.glGetObjectParameteriARB(f,ARBShaderObjects.GL_OBJECT_TYPE_ARB)==ARBShaderObjects.GL_SHADER_OBJECT_ARB,"core shader accepted by ARB object query");
        require(ARBShaderObjectsM.glGetObjectParameteriARB(q,ARBShaderObjects.GL_OBJECT_TYPE_ARB)==ARBShaderObjects.GL_PROGRAM_OBJECT_ARB,"core program accepted by ARB query");
        GL20M.glAttachShader(p,v);ARBShaderObjectsM.glAttachObjectARB(p,f);GL20M.glLinkProgram(p);
        require(ARBShaderObjectsM.glGetObjectParameteriARB(p,ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB)==1,"ARB-created program linked through core calls");
        ARBShaderObjectsM.glAttachObjectARB(q,v);GL20M.glAttachShader(q,f);ARBShaderObjectsM.glLinkProgramARB(q);
        require(GL20M.glGetProgrami(q,GL20.GL_LINK_STATUS)==1,"core-created program linked through ARB calls");
        GL20M.glUseProgram(p);require(ARBShaderObjectsM.glGetHandleARB(ARBShaderObjects.GL_PROGRAM_OBJECT_ARB)==p,"ARB current handle follows core binding");
        ByteBuffer n=name("u_Radius");int np=n.position(),nl=n.limit();int location=ARBShaderObjectsM.glGetUniformLocationARB(p,n);
        require(location==GL20M.glGetUniformLocation(p,"u_Radius")&&location>=0,"native uniform ByteBuffer name resolves same location");require(n.position()==np&&n.limit()==nl,"name cursor preserved");
        ARBShaderObjectsM.glUniform1fARB(location,8);require(GlProgram.getFloatUniform("u_Radius")[0]==8,"ARB uniform updates core-bound executable");
        ByteBuffer zero=output(0);IntBuffer length=IntBuffer.allocate(3);length.position(1);length.put(0,77);length.put(2,88);
        GL20M.glGetShaderSource(v,length,zero);require(length.get(1)==0&&zero.position()==2&&peek(zero,1)==91&&peek(zero,2)==91,"zero extent never writes a terminator/outside region");
        for(int extent:new int[]{1,8,vertex.getBytes(StandardCharsets.UTF_8).length+1}){
            ByteBuffer out=output(extent);int position=out.position(),limit=out.limit();ARBShaderObjectsM.glGetShaderSourceARB(v,length,out);int count=Math.min(vertex.getBytes(StandardCharsets.UTF_8).length,extent-1);
            require(length.get(1)==count&&out.position()==position&&out.limit()==limit&&length.position()==1,"reported byte extent and all cursors preserved");
            require(read(out,count).equals(vertex.substring(0,count))&&out.get(position+count)==0&&peek(out,position-1)==91&&peek(out,limit)==91,"original source, truncation, terminator and region sentinels");
        }
        require(length.get(0)==77&&length.get(2)==88,"length neighbors untouched");require(GL20M.glGetShaderSource(v,8).equals(vertex.substring(0,7)),"String source overload honors max extent");
        int invalid=GL20M.glCreateShader(GL20.GL_VERTEX_SHADER);GL20M.glShaderSource(invalid,"invalid GLSL !!!");ARBShaderObjectsM.glCompileShaderARB(invalid);String log=GL20M.glGetShaderInfoLog(invalid);require(!log.isEmpty()&&ARBShaderObjectsM.glGetInfoLogARB(invalid).equals(log),"ARB diagnostic is exact real native compiler error");
        ByteBuffer logOut=output(12);int[] len={-1,81};ARBShaderObjectsM.glGetInfoLogARB(invalid,len,logOut);require(len[0]==11&&len[1]==81&&read(logOut,11).equals(log.substring(0,11))&&logOut.get(13)==0,"truncated native diagnostic written to owned extent");
        int empty=GL20M.glCreateProgram();ARBShaderObjectsM.glLinkProgramARB(empty);require(ARBShaderObjectsM.glGetInfoLogARB(empty).equals(GL20M.glGetProgramInfoLog(empty))&&!ARBShaderObjectsM.glGetInfoLogARB(empty).isEmpty(),"real empty-link failure exposed through both APIs");
        ARBShaderObjectsM.glDeleteObjectARB(invalid);ARBShaderObjectsM.glDeleteObjectARB(empty);
        ARBShaderObjectsM.glDeleteObjectARB(v);GL20M.glDeleteShader(f);require(ARBShaderObjectsM.glGetObjectParameteriARB(v,ARBShaderObjects.GL_OBJECT_DELETE_STATUS_ARB)==1&&GL20M.glGetShaderi(f,GL20.GL_DELETE_STATUS)==1,"attached marked shader names remain valid across APIs");
        Object executable=GlProgram.currentNativeProgram();ARBShaderObjectsM.glDeleteObjectARB(p);
        require(GlProgram.currentProgramId()==p&&GlProgram.currentNativeProgram()==executable&&GL20M.glGetProgrami(p,GL20.GL_DELETE_STATUS)==1&&retired.isEmpty(),"deleting current program preserves executable, uniforms, attachments and GPU owner");
        ARBShaderObjectsM.glUseProgramObjectARB(q);require(GlProgram.currentProgramId()==q&&ARBShaderObjectsM.glGetHandleARB(ARBShaderObjects.GL_PROGRAM_OBJECT_ARB)==q&&!GlProgram.isProgram(p)&&retired.size()==1,"program retired exactly once when replaced");
        GL20M.glDeleteProgram(q);require(GlProgram.currentProgramId()==q&&retired.size()==1,"core current deletion is deferred");GL20M.glUseProgram(0);
        require(retired.size()==2&&!GlProgram.isProgram(q)&&!GlProgram.isShader(v)&&!GlProgram.isShader(f)&&ARBShaderObjectsM.glGetHandleARB(ARBShaderObjects.GL_PROGRAM_OBJECT_ARB)==0,"final unbind retires program and last attached shader owners exactly once");
        ARBShaderObjectsM.glDeleteObjectARB(q);GL20M.glDeleteShader(v);require(retired.size()==2,"repeated deletion cannot retire executable twice");
        System.out.println("NATIVE_SHADER_INTEROP_PASS checks="+checks+" actualNativeCompilation=true mixedCoreArbObjects=true logsAndSourcesExact=true bufferCursorsPreserved=true deferredDeletion=true");
    }
}
