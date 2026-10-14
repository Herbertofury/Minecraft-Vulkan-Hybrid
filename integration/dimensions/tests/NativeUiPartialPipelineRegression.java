package net.vulkanmod.vulkan.shader;

import org.lwjgl.vulkan.*;
import java.nio.LongBuffer;
import java.lang.reflect.InvocationTargetException;
import static org.lwjgl.vulkan.VK10.*;

/** A documented error-result control around real GPU pipeline creation/destruction. */
public final class NativeUiPartialPipelineRegression {
    static long createdHandle;
    static int created, destroyed;
    public static int create(VkDevice d,long cache,VkGraphicsPipelineCreateInfo.Buffer info,VkAllocationCallbacks callbacks,LongBuffer out) {
        int result=vkCreateGraphicsPipelines(d,cache,info,callbacks,out);
        if(result!=VK_SUCCESS) throw new AssertionError("Actual GPU control pipeline must create before result injection: "+result);
        createdHandle=out.get(0);if(createdHandle==0)throw new AssertionError("Actual native returned handle required");created++;
        // The driver succeeded; only its returned result is replaced for this control.
        return VK_ERROR_OUT_OF_HOST_MEMORY;
    }
    public static void destroy(VkDevice d,long pipeline,VkAllocationCallbacks callbacks) {
        if(pipeline!=createdHandle||destroyed!=0)throw new AssertionError("Returned pipeline must retire exactly once");
        vkDestroyPipeline(d,pipeline,callbacks);destroyed++;
    }
    public static void main(String[] args)throws Exception {
        boolean propagated=false;
        try { Class.forName("NativeUiGpuRegression").getMethod("main",String[].class).invoke(null,(Object)args); }
        catch(InvocationTargetException failure){
            if(!(failure.getCause() instanceof IllegalStateException)||!failure.getCause().getMessage().contains("VK_ERROR_OUT_OF_HOST_MEMORY"))throw failure;
            propagated=true;
        }
        if(!propagated||created!=1||destroyed!=1)throw new AssertionError("Partial returned native pipeline ownership: propagated="+propagated+" created="+created+" destroyed="+destroyed);
        System.out.println("NATIVE_UI_PARTIAL_PIPELINE_PASS actualGpuCreation=1 actualGpuDestruction=1 injectedResult=VK_ERROR_OUT_OF_HOST_MEMORY originalFailurePropagated=true");
    }
}
