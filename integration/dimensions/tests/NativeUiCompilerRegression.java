import net.vulkanmod.vulkan.shader.NativeUiProgram;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public final class NativeUiCompilerRegression {
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static String read(Path p)throws Exception{return Files.readString(p);}
    static void reject(Runnable r,String m){try{r.run();throw new AssertionError("invalid program accepted: "+m);}catch(IllegalArgumentException expected){}}
    static byte[] bytes(ByteBuffer b){byte[] out=new byte[b.remaining()];b.get(out);return out;}
    public static void main(String[] args)throws Exception{
        Path p=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        String rect=read(p.resolve("003-rect.vsh")),font=read(p.resolve("002-font.vsh"));
        String[] names={"circle","rounded_rect","font"};
        String[] fragments={read(p.resolve("000-circle.fsh")),read(p.resolve("004-rounded_rect.fsh")),read(p.resolve("001-font.fsh"))};
        for(int i=0;i<names.length;i++){
            String vertex=i==2?font:rect; NativeUiProgram a=NativeUiProgram.compile(names[i],vertex,fragments[i]);
            require(a.uniformBytes()>0,"real uniform block");
            require(a.attributes().stream().anyMatch(x->x.name().equals("mvh_Vertex")),"original vertex input");
            ByteBuffer u=a.allocateUniforms();float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            a.writeFloats(u,"mvh_ModelViewProjection",identity);
            if(i<2){a.writeFloats(u,"u_Radius",8);a.writeFloats(u,i==0?"u_CenterPos":"u_InnerRect",i==0?new float[]{16,16}:new float[]{8,8,24,24});}
            else {require(a.samplers().equals(List.of(new NativeUiProgram.Sampler("msdf",1))),"original sampler resource");a.writeFloats(u,"fgColor",1,0.5f,0.25f,1);a.writeFloats(u,"doffset",0.1f);a.writeFloats(u,"hint_amount",0.5f);a.writeFloats(u,"subpixel_amount",0);a.writeFloats(u,"sdf_texel",1f/32,1f/32);}
            byte[] vb=bytes(a.vertexSpirv()),fb=bytes(a.fragmentSpirv());
            require(ByteBuffer.wrap(vb).order(ByteOrder.LITTLE_ENDIAN).getInt()==0x07230203,"native vertex SPIR-V");
            ByteBuffer caller=a.vertexSpirv();caller.putInt(0,0);caller.position(caller.limit());
            require(Arrays.equals(vb,bytes(a.vertexSpirv())),"caller does not corrupt bytecode ownership");
            NativeUiProgram reload=NativeUiProgram.compile(names[i],vertex,fragments[i]);
            require(Arrays.equals(vb,bytes(reload.vertexSpirv()))&&Arrays.equals(fb,bytes(reload.fragmentSpirv())),"deterministic reload");
            Files.write(out.resolve(names[i]+".vsh.spv"),vb);Files.write(out.resolve(names[i]+".fsh.spv"),fb);
            System.out.println("ORIGINAL_UI_COMPILE="+names[i]+" uniformBytes="+a.uniformBytes()+" names="+a.uniformNames()+" attributes="+a.attributes()+" samplers="+a.samplers());
        }
        reject(()->NativeUiProgram.compile("varying-mismatch",rect,fragments[0].replace("varying vec2 f_Position","varying vec3 f_Position")),"varying type mismatch");
        reject(()->NativeUiProgram.compile("missing-varying",rect,fragments[0].replace("f_Position","missing_position")),"missing producer");
        reject(()->NativeUiProgram.compile("uniform-mismatch","uniform vec2 u_Radius;\n"+rect,fragments[0]),"cross-stage uniform type mismatch");
        reject(()->NativeUiProgram.compile("unsupported-builtin",rect.replace("gl_Vertex","gl_ClipVertex"),fragments[0]),"unsupported builtin");
        reject(()->NativeUiProgram.compile("comment","/* unterminated",fragments[0]),"unterminated comment");
        boolean bad=false;try{NativeUiProgram.compile("invalid-native",rect,fragments[0].replace("float v =","float v !!!"));}catch(RuntimeException e){bad=e.getMessage().contains("Failed to compile shader");}
        require(bad,"invalid body rejected by actual native compiler");
        System.out.println("NATIVE_UI_COMPILER_PASS originals=3 negativeControls=6");
    }
}
