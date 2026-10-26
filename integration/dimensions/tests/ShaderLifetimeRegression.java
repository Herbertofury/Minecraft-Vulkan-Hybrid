import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.SPIRVUtils.SPIRV;
import net.vulkanmod.vulkan.shader.SPIRVUtils.ShaderKind;
import java.nio.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Actual shaderc operations; counters are injected only around native result acquisition/release. */
public final class ShaderLifetimeRegression {
    private static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static final String VALID = "#version 450\nlayout(location=0) out vec4 color;void main(){color=vec4(.25,.5,.75,1);}";
    public static void main(String[] args) throws Exception {
        String digest = null;
        for (int i=0; i<80; i++) {
            SPIRV result = SPIRVUtils.compileShader("lifetime.fsh", VALID, ShaderKind.FRAGMENT_SHADER);
            ByteBuffer bytes = result.bytecode().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            require(bytes.remaining()>20 && bytes.getInt(bytes.position())==0x07230203, "real complete SPIR-V");
            byte[] copy = new byte[bytes.remaining()]; bytes.get(copy);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(copy));
            if (digest==null) digest=hash; else require(digest.equals(hash), "shader bytecode changed");
            result.free(); result.free();
        }
        for (int i=0; i<80; i++) {
            try {
                SPIRVUtils.compileShader("invalid.fsh", "#version 450\nvoid main(){invalid_shader;}", ShaderKind.FRAGMENT_SHADER);
                throw new AssertionError("invalid GLSL accepted");
            } catch (RuntimeException expected) {
                require(expected.getMessage().contains("Failed to compile shader invalid.fsh"), "native compile error identity");
            }
        }
        ByteBuffer borrowed = ByteBuffer.allocateDirect(32).order(ByteOrder.LITTLE_ENDIAN);
        borrowed.putInt(0, 0x07230203);
        SPIRV wrapper = new SPIRV(0, borrowed); wrapper.free(); wrapper.free();
        require(borrowed.getInt(0)==0x07230203, "borrowed caller buffer preserved");
        require(SPIRVUtils.liveTestResults()==0, "unreleased native compiler results: "+SPIRVUtils.liveTestResults());
        require(SPIRVUtils.acquiredTestResults()==160 && SPIRVUtils.releasedTestResults()==160, "all success/failure results released once");
        try { wrapper.bytecode(); throw new AssertionError("released wrapper usable"); }
        catch (IllegalStateException expected) { }
        System.out.println("SHADER_LIFETIME_PASS acquired=160 released=160 live=0 spirv_sha256="+digest);
    }
}
