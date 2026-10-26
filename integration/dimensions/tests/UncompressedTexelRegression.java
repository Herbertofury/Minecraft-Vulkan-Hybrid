import net.vulkanmod.vulkan.texture.UncompressedTexelSize;
import net.vulkanmod.vulkan.texture.TextureUploadSpan;
import static org.lwjgl.vulkan.VK10.*;

/** Upload bounds for real Vulkan float/integer format footprints, including overflow/rejection. */
public final class UncompressedTexelRegression {
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void reject(Runnable action){try{action.run();throw new AssertionError("Unsupported input accepted");}catch(IllegalArgumentException|ArithmeticException expected){}}
    public static void main(String[] args){
        int[][] formats={{VK_FORMAT_R8_UNORM,1},{VK_FORMAT_R8_UINT,1},{VK_FORMAT_R8G8_UNORM,2},{VK_FORMAT_R16_SFLOAT,2},{VK_FORMAT_R16G16_SFLOAT,4},{VK_FORMAT_R32_SFLOAT,4},{VK_FORMAT_R32_UINT,4},{VK_FORMAT_R32_SINT,4},{VK_FORMAT_R8G8B8A8_UNORM,4},{VK_FORMAT_R8G8B8A8_SRGB,4},{VK_FORMAT_B8G8R8A8_UNORM,4},{VK_FORMAT_R16G16B16A16_SFLOAT,8},{VK_FORMAT_R32G32_SFLOAT,8},{VK_FORMAT_R32G32B32A32_SFLOAT,16},{VK_FORMAT_R32G32B32A32_UINT,16},{VK_FORMAT_D32_SFLOAT,4},{VK_FORMAT_D24_UNORM_S8_UINT,4},{VK_FORMAT_D32_SFLOAT_S8_UINT,8}};
        for(int[] f:formats){int size=UncompressedTexelSize.bytes(f[0]);require(size==f[1],"Vulkan format footprint "+f[0]);require(TextureUploadSpan.byteSize(3,2,4,size)==7*f[1],"Padded final row span");require(TextureUploadSpan.sourceOffset(4,1,1,size)==5*f[1],"Padded skipped source origin");}
        require(TextureUploadSpan.byteSize(2,2,2,UncompressedTexelSize.bytes(VK_FORMAT_R32_SFLOAT))==16,"Exact four float depth texels");
        reject(()->UncompressedTexelSize.bytes(VK_FORMAT_UNDEFINED));reject(()->UncompressedTexelSize.bytes(VK_FORMAT_BC1_RGB_UNORM_BLOCK));reject(()->UncompressedTexelSize.bytes(-1));reject(()->TextureUploadSpan.byteSize(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,16));reject(()->TextureUploadSpan.sourceOffset(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,16));
        System.out.println("NATIVE_UNCOMPRESSED_TEXEL_UPLOAD_PASS "+formats.length+" formats, padded offsets/extents, float depth, unsupported blocks and overflow");
    }
}
