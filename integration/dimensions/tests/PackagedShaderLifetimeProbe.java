import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.SPIRVUtils.SPIRV;
import java.nio.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Load the actual packaged class, no fixture or instrumentation. */
public final class PackagedShaderLifetimeProbe {
    public static void main(String[] args) throws Exception {
        SPIRV result=SPIRVUtils.compileShader("packaged.fsh","#version 450\nlayout(location=0) out vec4 color;void main(){color=vec4(.25,.5,.75,1);}",SPIRVUtils.ShaderKind.FRAGMENT_SHADER);
        ByteBuffer code=result.bytecode().duplicate();byte[] bytes=new byte[code.remaining()];code.get(bytes);
        System.out.println("SPIRV_SHA256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Field handle=SPIRV.class.getDeclaredField("handle");handle.setAccessible(true);
        if(handle.getLong(result)==0)throw new AssertionError("no native compiler owner");
        result.free();result.free();
        if(handle.getLong(result)!=0)throw new AssertionError("packaged native owner not retired");
        try {result.bytecode();throw new AssertionError("released owner exposed");}catch(IllegalStateException expected){}
        try {SPIRVUtils.compileShader("invalid.fsh","#version 450\ninvalid",SPIRVUtils.ShaderKind.FRAGMENT_SHADER);throw new AssertionError("invalid GLSL accepted");}
        catch(RuntimeException expected){if(!expected.getMessage().contains("Failed to compile shader invalid.fsh"))throw expected;}
        System.out.println("PACKAGED_NATIVE_SHADER_LIFETIME_PASS");
    }
}
