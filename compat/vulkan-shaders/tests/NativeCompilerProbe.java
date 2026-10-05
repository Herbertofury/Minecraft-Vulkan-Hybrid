import net.irisshaders.iris.vulkan.shader.IrisSPIRVCompiler;
import net.irisshaders.iris.gl.shader.ShaderType;
import java.nio.*;
/** Actual shaderc native compilation with the pinned reference compiler, not GPU/FPS acceptance. */
public final class NativeCompilerProbe {
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void spirv(ByteBuffer data){require(data.isDirect()&&data.remaining()>20&&data.remaining()%4==0,"direct complete SPIR-V");require(data.order(ByteOrder.LITTLE_ENDIAN).getInt(data.position())==0x07230203,"SPIR-V magic");}
    public static void main(String[] ignored){
        IrisSPIRVCompiler.clearCache();
        String fragment="#version 450\nlayout(location=0) out vec4 color;void main(){color=vec4(0.25,0.5,0.75,1.0);}";
        ByteBuffer first=IrisSPIRVCompiler.compile("composite",fragment,ShaderType.FRAGMENT);spirv(first);int bytes=first.remaining();
        first.putInt(0,0);first.position(first.limit());
        ByteBuffer second=IrisSPIRVCompiler.compile("composite",fragment,ShaderType.FRAGMENT);spirv(second);require(second.remaining()==bytes,"caller cursor/content isolation");
        second.putInt(0,0);second.position(second.limit());spirv(IrisSPIRVCompiler.compile("composite",fragment,ShaderType.FRAGMENT));
        require(IrisSPIRVCompiler.getCacheSize()==1,"same full input cache reuse");
        String a=fragment+"//Aa",b=fragment+"//BB";require(a.hashCode()==b.hashCode()&&!a.equals(b),"real Java source hash collision");
        spirv(IrisSPIRVCompiler.compile("composite",a,ShaderType.FRAGMENT));spirv(IrisSPIRVCompiler.compile("composite",b,ShaderType.FRAGMENT));
        require(IrisSPIRVCompiler.getCacheSize()==3,"full source collision isolation");
        spirv(IrisSPIRVCompiler.compilePreprocessed("composite",fragment,ShaderType.FRAGMENT));require(IrisSPIRVCompiler.getCacheSize()==4,"raw/preprocessed isolation");
        spirv(IrisSPIRVCompiler.compile("shadow",fragment,ShaderType.FRAGMENT));require(IrisSPIRVCompiler.getCacheSize()==5,"name isolation");
        String vertex="#version 450\nlayout(location=0) in vec3 Position;void main(){gl_Position=vec4(Position,1.0);}";
        spirv(IrisSPIRVCompiler.compile("terrain",vertex,ShaderType.VERTEX));
        String compute="#version 450\nlayout(local_size_x=1) in;layout(set=0,binding=0,std430) buffer Data{uint value;};void main(){value=17u;}";
        spirv(IrisSPIRVCompiler.compilePreprocessed("cull",compute,ShaderType.COMPUTE));
        int count=IrisSPIRVCompiler.getCacheSize();
        try{IrisSPIRVCompiler.compile("invalid","#version 450\nvoid main(){not_valid_glsl;}",ShaderType.FRAGMENT);throw new AssertionError("invalid GLSL accepted");}catch(RuntimeException expected){require(expected.getMessage().contains("SPIR-V compilation failed"),"actual native error propagation");}
        require(IrisSPIRVCompiler.getCacheSize()==count,"failed source must not enter cache");
        IrisSPIRVCompiler.clearCache();require(IrisSPIRVCompiler.getCacheSize()==0,"reload clear");
        System.out.println("IRIS_REFERENCE_NATIVE_SHADERC_PASS vertex fragment compute input cursor content errors clear");
    }
}
