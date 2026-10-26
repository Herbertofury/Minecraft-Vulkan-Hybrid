import mvhshadercompat.SpirvUniformBlock;
import net.irisshaders.iris.vulkan.shader.IrisSPIRVCompiler;
import net.irisshaders.iris.gl.shader.ShaderType;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Real shaderc output provides the ABI; checks are independent of helper offsets. */
public final class NativeUniformProbe {
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Unsafe uniform input accepted");}catch(IllegalArgumentException expected){}}
    private static ByteBuffer copy(ByteBuffer data){ByteBuffer b=ByteBuffer.allocate(data.remaining()).order(ByteOrder.LITTLE_ENDIAN);b.put(data.duplicate()).flip();return b;}
    private static ByteBuffer decorate(ByteBuffer data,int member,int kind,int replacement,boolean replaceKind){
        ByteBuffer out=copy(data);int matches=0;
        for(int p=20;p<out.limit();){int h=out.getInt(p),wc=h>>>16;
            if((h&65535)==72&&out.getInt(p+8)==member&&out.getInt(p+12)==kind){out.putInt(p+(replaceKind?12:16),replacement);matches++;}
            p+=wc*4;
        }
        require(matches==1,"Unique native decoration mutation");return out;
    }
    public static void fill(SpirvUniformBlock block,ByteBuffer data){
        block.writeFloats(data,0,0.25f,0.5f,0.75f);block.writeFloats(data,1,1.0f);
        block.writeFloats(data,2,1,2,3,4,5,6,7,8,9);block.writeFloats(data,3,0.125f,0.25f,0.5f);
        block.writeFloats(data,4,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16);
        block.writeInts(data,5,17,23);block.writeInts(data,6,31);
    }
    public static void main(String[] args)throws Exception{
        String source=Files.readString(Path.of(args[0]));ByteBuffer spv=IrisSPIRVCompiler.compilePreprocessed("reference-uniform-fragment",source,ShaderType.FRAGMENT);
        int position=spv.position(),limit=spv.limit();SpirvUniformBlock block=SpirvUniformBlock.reflect(spv,0,0);
        require(spv.position()==position&&spv.limit()==limit,"Reflection owns cursor");
        int[] offsets={0,12,16,64,112,176,184};require(block.bytes()==192&&block.fields().size()==7,"Native block total extent");
        for(int i=0;i<offsets.length;i++)require(block.fields().get(i).offset()==offsets[i],"Actual compiler member offset "+i);
        require(block.fields().get(2).matrixStride()==16&&block.fields().get(3).arrayStride()==16,"Actual native array/matrix strides");
        ByteBuffer data=block.allocate();data.position(7);fill(block,data);require(data.position()==7&&data.limit()==192,"Writer preserves cursor/limit");
        for(int c=0;c<3;c++)for(int r=0;r<3;r++)require(data.getFloat(16+c*16+r*4)==c*3+r+1,"mat3 column padding/order");
        require(data.getFloat(64)==0.125f&&data.getFloat(80)==0.25f&&data.getFloat(96)==0.5f,"array extended alignment");
        for(int c=0;c<4;c++)for(int r=0;r<4;r++)require(data.getFloat(112+c*16+r*4)==c*4+r+1,"mat4 column order");
        require(data.getInt(176)==17&&data.getInt(180)==23&&data.getInt(184)==31,"integer vector/scalar ABI");
        int[] padding={28,44,60,68,72,76,84,88,92,100,104,108,188};for(int p:padding)require(data.getInt(p)==0,"Untouched ABI padding");
        ByteBuffer snapshot=copy(data.duplicate().position(0));
        reject(()->block.writeInts(data,0,1,2,3));reject(()->block.writeFloats(data,2,1,2));
        reject(()->block.writeFloats(data.asReadOnlyBuffer(),1,1));reject(()->block.writeFloats(ByteBuffer.allocate(8),1,1));
        require(snapshot.equals(data.duplicate().position(0)),"Rejected writes leave payload intact");
        reject(()->SpirvUniformBlock.reflect(decorate(spv,2,35,0,false),0,0));
        reject(()->SpirvUniformBlock.reflect(decorate(spv,2,7,12,false),0,0));
        reject(()->SpirvUniformBlock.reflect(decorate(spv,2,5,4,true),0,0));
        reject(()->SpirvUniformBlock.reflect(decorate(spv,3,35,999,true),0,0));
        reject(()->SpirvUniformBlock.reflect(spv,1,0));
        ByteBuffer bad=copy(spv);bad.putInt(20,0);reject(()->SpirvUniformBlock.reflect(bad,0,0));
        ByteBuffer shortHeader=copy(spv).limit(16);reject(()->SpirvUniformBlock.reflect(shortHeader,0,0));
        ByteBuffer badMagic=copy(spv);badMagic.putInt(0,0);reject(()->SpirvUniformBlock.reflect(badMagic,0,0));
        if(args.length==2){Path out=Path.of(args[1]);Files.createDirectory(out);byte[] raw=new byte[spv.remaining()];spv.duplicate().get(raw);Files.write(out.resolve("reference-uniform-fragment.spv"),raw);Files.writeString(out.resolve("reference-uniform-fragment.spv.glsl"),source);}
        System.out.println("IRIS_REFERENCE_NATIVE_UNIFORM_ABI_PASS 7members 192bytes reflected offsets strides padded writes malformed/reordered/row-major/type rejection");
    }
}
