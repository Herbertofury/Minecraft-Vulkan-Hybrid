import java.nio.*;
import java.nio.file.*;
import mvhshadercompat.SpirvUniformBlock;
import net.irisshaders.iris.vulkan.shader.IrisSPIRVCompiler;
import net.irisshaders.iris.gl.shader.ShaderType;

/** Full pinned compiler and real original cull SPIR-V; no GPU/FPS acceptance. */
public final class NativeCullLayoutProbe {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void reject(Runnable action){try{action.run();throw new AssertionError("Unsupported layout/write accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        Path shaders=Path.of(args[0]),out=Path.of(args[1]);String[] names={"apply.comp","transformed.vert","shaft.frag","cull.comp"};ShaderType[] types={ShaderType.COMPUTE,ShaderType.VERTEX,ShaderType.FRAGMENT,ShaderType.COMPUTE};
        for(int i=0;i<names.length;i++){
            ByteBuffer b=IrisSPIRVCompiler.compilePreprocessed(i==3?"original_flywheel_cull":names[i],Files.readString(shaders.resolve(names[i])),types[i]);
            byte[] bytes=new byte[b.remaining()];b.get(bytes);Files.write(out.resolve(names[i]+".spv"),bytes);
        }
        ByteBuffer code=ByteBuffer.wrap(Files.readAllBytes(out.resolve("cull.comp.spv")));reject(()->SpirvUniformBlock.reflect(code,1,0));
        var layout=SpirvUniformBlock.reflectStructured(code,1,0);require(layout.bytes()==864&&layout.fields().size()==46,"Original complete frame block extent/leaves");
        require(layout.fieldOrdinal(0,3)==3&&layout.fields().get(3).offset()==48,"Nested frustum xyW path/offset");require(layout.fields().get(layout.fieldOrdinal(1,0)).offset()==96,"Nested near-plane offset");require(layout.fields().get(layout.fieldOrdinal(2)).offset()==128,"View matrix offset");
        ByteBuffer data=layout.allocate();data.position(9);layout.writeFloats(data,layout.fieldOrdinal(0,3),1,2,3,4);require(data.position()==9&&data.order(ByteOrder.LITTLE_ENDIAN).getFloat(48)==1&&data.getFloat(60)==4,"Nested writes preserve cursor/offset");
        float[] matrix=new float[16];for(int i=0;i<16;i++)matrix[i]=i+1;layout.writeFloats(data,layout.fieldOrdinal(2),matrix);for(int i=0;i<16;i++)require(data.getFloat(128+i*4)==matrix[i],"Column-major view matrix");
        layout.writeInts(data,layout.fieldOrdinal(1,6),7);require(data.getInt(120)==7,"Nested integer field offset");reject(()->layout.fieldOrdinal(0,99));reject(()->layout.writeFloats(data,layout.fieldOrdinal(1,6),7));reject(()->layout.writeFloats(data.asReadOnlyBuffer(),3,1,2,3,4));reject(()->SpirvUniformBlock.reflectStructured(code,1,7));
        StringBuilder abi=new StringBuilder("{\"bytes\":"+layout.bytes()+",\"fields\":[");for(int i=0;i<layout.fields().size();i++){var f=layout.fields().get(i);if(i>0)abi.append(',');abi.append("{\"path\":"+layout.memberPath(i)+",\"offset\":"+f.offset()+",\"extent\":"+f.extent()+"}");}abi.append("]}");Files.writeString(out.resolve("FRAME-ABI.json"),abi);
        System.out.println("ORIGINAL_FLYWHEEL_NATIVE_SHADER_AND_STRUCTURED_ABI_PASS four stages,864 bytes,46 leaves,paths,cursors,matrix,int and rejection controls");
    }
}
