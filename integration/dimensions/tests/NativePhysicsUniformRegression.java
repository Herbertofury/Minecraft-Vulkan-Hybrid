import net.vulkanmod.gl.GlProgram;
import net.vulkanmod.gl.NativeGlErrors;
import net.vulkanmod.vulkan.shader.NativeUiProgram;
import java.nio.*;
import java.util.*;

public final class NativePhysicsUniformRegression {
    // Specification constants only; this native compiler SDK has no GL binding/context.
    private static final int GL_VERTEX_SHADER=0x8B31,GL_FRAGMENT_SHADER=0x8B30,GL_COMPILE_STATUS=0x8B81,GL_LINK_STATUS=0x8B82,GL_ACTIVE_UNIFORMS=0x8B86;
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static int stage(int kind,String source){int id=GlProgram.createShader(kind);GlProgram.shaderSource(id,source);GlProgram.compileShader(id);require(GlProgram.getShaderi(id,GL_COMPILE_STATUS)==1,GlProgram.getShaderInfoLog(id));return id;}
    public static void main(String[] ignored){
        String vertex="attribute vec3 Position; uniform mat2 rotate2[2]; uniform mat3 rotate3; uniform mat4 project4; void main(){vec3 p=rotate3*vec3(Position.xy,1);vec2 q=rotate2[1]*p.xy;gl_Position=project4*vec4(q,0,1);}";
        String fragment="uniform float weights[4];uniform vec2 offsets[2];uniform vec3 palette[2];uniform vec4 tint;uniform int state;uniform float dead;out vec4 resultColor;void main(){resultColor=vec4(weights[3]+offsets[1].x,palette[1].g,tint.b,float(state)/255.0);}";
        int v=stage(GL_VERTEX_SHADER,vertex),f=stage(GL_FRAGMENT_SHADER,fragment),p=GlProgram.createProgram();GlProgram.attachShader(p,v);GlProgram.attachShader(p,f);GlProgram.bindAttribLocation(p,0,"Position");GlProgram.bindFragDataLocation(p,0,"resultColor");GlProgram.linkProgram(p);require(GlProgram.getProgrami(p,GL_LINK_STATUS)==1,GlProgram.getProgramInfoLog(p));GlProgram.useProgram(p);
        NativeUiProgram nativeProgram=GlProgram.currentNativeProgram();require(nativeProgram.attributes().equals(List.of(new NativeUiProgram.Attribute("Position","vec3",0))),"bound native attribute");
        require(nativeProgram.uniform("dead")==null && GlProgram.getUniformLocation(p,"dead")==-1,"actual optimized unused uniform excluded");
        require(GlProgram.getProgrami(p,GL_ACTIVE_UNIFORMS)==8,"actual active uniform count");
        Set<String> reflected=new HashSet<>();for(int i=0;i<8;i++){var u=GlProgram.activeUniform(p,i);reflected.add(u.reflectedName());require(GlProgram.uniformType(u)!=0,"real uniform type");}
        require(reflected.contains("rotate2[0]") && reflected.contains("weights[0]"),"original array enumeration spelling");
        int weights=GlProgram.getUniformLocation(p,"weights"),w0=GlProgram.getUniformLocation(p,"weights[0]"),w3=GlProgram.getUniformLocation(p,"weights[3]");require(weights==w0 && w3==weights+3,"contiguous owned array locations");
        FloatBuffer data=FloatBuffer.wrap(new float[]{99,.1f,.2f,.3f,.4f,77});data.position(1).limit(5);GlProgram.uniformFloats(weights,1,data);require(data.position()==1 && data.limit()==5,"caller cursor preserved");require(Arrays.equals(GlProgram.getFloatUniform("weights"),new float[]{.1f,.2f,.3f,.4f}),"all scalar array elements stored");
        GlProgram.uniform1f(weights+1,.25f);require(GlProgram.getFloatUniform("weights")[1]==.25f && NativeGlErrors.consume()==0,"arithmetic array location works without prior lookup");
        GlProgram.uniformFloats(w3,1,new float[]{.8f,.9f});require(GlProgram.getFloatUniform("weights")[3]==.8f,"array tail clamps without changing other values");
        int rotate2=GlProgram.getUniformLocation(p,"rotate2");FloatBuffer matrix=FloatBuffer.wrap(new float[]{1,2,3,4,5,6,7,8});GlProgram.uniformMatrix(rotate2,2,true,matrix);require(Arrays.equals(GlProgram.getFloatUniform("rotate2"),new float[]{1,3,2,4,5,7,6,8}),"both mat2 transposes");require(matrix.position()==0,"matrix array cursor preserved");
        int rotate3=GlProgram.getUniformLocation(p,"rotate3");GlProgram.uniformMatrix(rotate3,3,true,FloatBuffer.wrap(new float[]{1,2,3,4,5,6,7,8,9}));require(Arrays.equals(GlProgram.getFloatUniform("rotate3"),new float[]{1,4,7,2,5,8,3,6,9}),"mat3 transpose");
        int tint=GlProgram.getUniformLocation(p,"tint");GlProgram.uniformFloats(tint,4,new float[]{.1f,.2f,.3f,.4f});GlProgram.uniform1i(tint,9);require(NativeGlErrors.consume()==1282 && GlProgram.getFloatUniform("tint")[2]==.3f,"wrong type rejects before mutation");
        GlProgram.uniformFloats(tint,4,new float[]{1,2,3,4,5,6,7,8});require(NativeGlErrors.consume()==1282 && GlProgram.getFloatUniform("tint")[2]==.3f,"scalar rejects multi-value update");
        GlProgram.uniformFloats(-1,3,new float[]{1,2,3});require(NativeGlErrors.consume()==0,"minus-one location ignored with current executable");
        int state=GlProgram.getUniformLocation(p,"state");GlProgram.uniform1i(state,255);GlProgram.uniformFloats(GlProgram.getUniformLocation(p,"offsets"),2,new float[]{1,2,3,4});GlProgram.uniformFloats(GlProgram.getUniformLocation(p,"palette"),3,new float[]{1,2,3,4,5,6});GlProgram.uniformMatrix4fv(GlProgram.getUniformLocation(p,"project4"),false,FloatBuffer.wrap(new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}));
        ByteBuffer upload=GlProgram.currentNativeUniforms().order(ByteOrder.LITTLE_ENDIAN);require(upload.remaining()==nativeProgram.uniformBytes(),"actual reflected upload extent");
        GlProgram.linkProgram(p);GlProgram.uniform1f(w3,42);require(NativeGlErrors.consume()==1282 && GlProgram.getFloatUniform("weights")==null,"relink retires all derived locations");
        GlProgram.deleteProgram(p);GlProgram.useProgram(0);GlProgram.deleteShader(v);GlProgram.deleteShader(f);
        System.out.println("NATIVE_PHYSICS_UNIFORM_REGRESSION_PASS actualSPIRV=true activeReflection=true arrays=true matrices=true transpose=true types=true callerCursors=true relinkOwnership=true");
    }
}
