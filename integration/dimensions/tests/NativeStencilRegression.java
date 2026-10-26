import net.vulkanmod.vulkan.shader.NativeStencilPipelineState;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkStencilOpState;
import static org.lwjgl.vulkan.VK10.*;

/** Exhaustive GL operation combinations against actual LWJGL native pipeline structures. */
public final class NativeStencilRegression {
    private static final int[] GL_OPS={7680,0,7681,7682,7683,5386,34055,34056};
    private static final int[] VK_OPS={VK_STENCIL_OP_KEEP,VK_STENCIL_OP_ZERO,VK_STENCIL_OP_REPLACE,VK_STENCIL_OP_INCREMENT_AND_CLAMP,VK_STENCIL_OP_DECREMENT_AND_CLAMP,VK_STENCIL_OP_INVERT,VK_STENCIL_OP_INCREMENT_AND_WRAP,VK_STENCIL_OP_DECREMENT_AND_WRAP};
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void face(VkStencilOpState face,int compare,int fail,int pass,int depthFail){
        require(face.compareOp()==compare,"compare");require(face.failOp()==fail,"stencil fail");
        require(face.passOp()==pass,"depth pass");require(face.depthFailOp()==depthFail,"depth fail");
        require(face.compareMask()==255&&face.writeMask()==255&&face.reference()==0,"initial dynamic values");
    }
    public static void main(String[] ignored){
        int checked=0;
        int[] formats={VK_FORMAT_D32_SFLOAT,VK_FORMAT_D24_UNORM_S8_UINT,VK_FORMAT_D32_SFLOAT_S8_UINT};
        try(VkPipelineDepthStencilStateCreateInfo target=VkPipelineDepthStencilStateCreateInfo.calloc()){
            for(int format:formats){
                boolean present=format!=VK_FORMAT_D32_SFLOAT;
                require(NativeStencilPipelineState.attachmentFormat(format)==(present?format:VK_FORMAT_UNDEFINED),"dynamic rendering stencil format");
                for(int enabled=0;enabled<2;enabled++)for(int compare=0;compare<8;compare++)for(int fail=0;fail<8;fail++)for(int pass=0;pass<8;pass++)for(int depthFail=0;depthFail<8;depthFail++){
                    int state=PipelineState.StencilState.encode(enabled==1,512+compare,GL_OPS[fail],GL_OPS[pass],GL_OPS[depthFail]);
                    NativeStencilPipelineState.apply(target,state,format);
                    require(target.stencilTestEnable()==(enabled==1&&present),"attachment/enable behavior");
                    face(target.front(),compare,VK_OPS[fail],VK_OPS[pass],VK_OPS[depthFail]);
                    face(target.back(),compare,VK_OPS[fail],VK_OPS[pass],VK_OPS[depthFail]);checked++;
                }
            }
        }
        try{PipelineState.StencilState.encode(true,999,7680,7680,7680);throw new AssertionError("invalid compare accepted");}catch(RuntimeException expected){}
        try{PipelineState.StencilState.encode(true,519,999,7680,7680);throw new AssertionError("invalid operation accepted");}catch(RuntimeException expected){}
        System.out.println("NATIVE_STENCIL_STRUCT_COMBINATIONS_PASS "+checked);
    }
}
