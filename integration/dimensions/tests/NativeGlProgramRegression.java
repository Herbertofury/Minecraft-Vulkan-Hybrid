import net.vulkanmod.gl.GlProgram;
import net.vulkanmod.vulkan.shader.NativeUiProgram;
import org.lwjgl.opengl.GL20;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public final class NativeGlProgramRegression {
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static int shader(int type,String source){int s=GlProgram.createShader(type);require(GlProgram.getShaderi(s,GL20.GL_COMPILE_STATUS)==0,"no fake precompile success");GlProgram.shaderSource(s,source);GlProgram.compileShader(s);require(GlProgram.getShaderi(s,GL20.GL_COMPILE_STATUS)==1,"actual native stage compile: "+GlProgram.getShaderInfoLog(s));return s;}
    static int link(int v,int f){int p=GlProgram.createProgram();GlProgram.attachShader(p,v);GlProgram.attachShader(p,f);GlProgram.linkProgram(p);require(GlProgram.getProgrami(p,GL20.GL_LINK_STATUS)==1,"native pair linked: "+GlProgram.getProgramInfoLog(p));return p;}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);String vs=Files.readString(root.resolve("003-rect.vsh")),fs=Files.readString(root.resolve("000-circle.fsh"));
        int invalid=GlProgram.createShader(GL20.GL_VERTEX_SHADER);GlProgram.shaderSource(invalid,"invalid GLSL !!!");GlProgram.compileShader(invalid);require(GlProgram.getShaderi(invalid,GL20.GL_COMPILE_STATUS)==0&&!GlProgram.getShaderInfoLog(invalid).isEmpty(),"actual native invalid GLSL log");GlProgram.deleteShader(invalid);
        int empty=GlProgram.createProgram();GlProgram.linkProgram(empty);require(GlProgram.getProgrami(empty,GL20.GL_LINK_STATUS)==0&&!GlProgram.getProgramInfoLog(empty).isEmpty(),"empty pair rejected");GlProgram.deleteProgram(empty);require(!GlProgram.isProgram(0),"zero is not a program object");
        int v=shader(GL20.GL_VERTEX_SHADER,vs),f=shader(GL20.GL_FRAGMENT_SHADER,fs);int p=link(v,f),q=link(v,f);
        GlProgram.useProgram(p);int radius=GlProgram.getUniformLocation(p,"u_Radius");require(radius>=0&&GlProgram.getUniformLocation(p,"missing_uniform")==-1,"reflected uniform identity");GlProgram.uniform1f(radius,8);require(GlProgram.getFloatUniform("u_Radius")[0]==8,"original uniform recorded");
        GlProgram.useProgram(q);GlProgram.uniform1f(radius,99);require(GlProgram.getFloatUniform("u_Radius")==null,"location from another program cannot write");
        int qr=GlProgram.getUniformLocation(q,"u_Radius");GlProgram.uniform1f(qr,4);NativeUiProgram compiled=GlProgram.currentNativeProgram();require(compiled!=null&&compiled.uniformBytes()==80,"real linked SPIR-V/reflected ABI");
        int matrix=GlProgram.getUniformLocation(q,"mvh_ModelViewProjection");float[] row={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};FloatBuffer b=FloatBuffer.wrap(row);GlProgram.uniformMatrix4fv(matrix,true,b);require(b.position()==0,"caller cursor preserved");float[] actual=GlProgram.getFloatUniform("mvh_ModelViewProjection");for(int i=0;i<16;i++)require(actual[i]==row[(i%4)*4+i/4],"matrix transpose preserved");require(GlProgram.currentNativeUniforms().remaining()==80,"actual reflected uniform upload bytes");
        GlProgram.detachShader(p,v);GlProgram.detachShader(p,f);GlProgram.deleteShader(v);GlProgram.deleteShader(f);require(GlProgram.getShaderi(v,GL20.GL_DELETE_STATUS)==1,"attached deleted shader retained");
        byte[] before=new byte[compiled.vertexSpirv().remaining()];compiled.vertexSpirv().get(before);GlProgram.linkProgram(p);require(GlProgram.getProgrami(p,GL20.GL_LINK_STATUS)==0,"empty relink rejected");
        GlProgram.deleteProgram(p);require(GlProgram.currentNativeProgram()==compiled,"another deleted program does not lose current executable");
        GlProgram.detachShader(q,v);GlProgram.detachShader(q,f);require(GlProgram.getShaderi(v,GL20.GL_SHADER_TYPE)==0,"last detached deleted shader retired");
        GlProgram.linkProgram(q);require(GlProgram.getProgrami(q,GL20.GL_LINK_STATUS)==0&&GlProgram.currentNativeProgram()==compiled,"failed relink retains current executable");
        GlProgram.deleteProgram(q);require(GlProgram.currentProgramId()==q&&GlProgram.currentNativeProgram()==compiled&&GlProgram.isProgram(q)&&GlProgram.getProgrami(q,GL20.GL_DELETE_STATUS)==1,"current deleted program retains executable and object");
        GlProgram.useProgram(0);require(GlProgram.currentProgramId()==0&&!GlProgram.isProgram(q),"deleted program retired when unbound");
        int uniqueShader=GlProgram.createShader(GL20.GL_VERTEX_SHADER),uniqueProgram=GlProgram.createProgram();require(uniqueShader!=uniqueProgram,"shared core/ARB object namespace");GlProgram.deleteObject(uniqueShader);require(!GlProgram.isShader(uniqueShader)&&GlProgram.isProgram(uniqueProgram),"shader object delete cannot target program");GlProgram.deleteObject(uniqueProgram);require(!GlProgram.isProgram(uniqueProgram),"program object delete dispatch");
        System.out.println("NATIVE_GL_PROGRAM_PASS realStageCompile=true realPairLink=true invalidAndEmptyRejected=true locationsOwned=true matrixTranspose=true deletedStageLifetime=true failedRelinkOwnership=true sharedObjectNamespace=true currentDeleteLifetime=true");
    }
}
