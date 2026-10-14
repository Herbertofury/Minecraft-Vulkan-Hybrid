import net.vulkanmod.gl.GlProgram;
import net.vulkanmod.gl.GlSync;

/** Execute only pinned Hari metadata adapters, without a game, GL context or third-party initializer. */
public final class NativeProgramGapProbe {
    public static void main(String[] args) {
        int shader=GlProgram.createShader(0x8B31);
        System.out.println("status_before_compile="+GlProgram.getShaderi(shader,0x8B81));
        GlProgram.shaderSource(shader,"THIS IS DELIBERATELY INVALID GLSL {");
        GlProgram.compileShader(shader);
        System.out.println("invalid_glsl_compile_status="+GlProgram.getShaderi(shader,0x8B81));
        System.out.println("invalid_glsl_log_length="+GlProgram.getShaderInfoLog(shader).length());
        int program=GlProgram.createProgram();
        GlProgram.linkProgram(program);
        System.out.println("empty_program_link_status="+GlProgram.getProgrami(program,0x8B82));
        long sync=GlSync.fenceSync(GlSync.GL_SYNC_GPU_COMMANDS_COMPLETE,0);
        System.out.println("unsubmitted_fence_wait_result="+GlSync.clientWaitSync(sync,0,0));
        System.out.println("unsubmitted_fence_status="+GlSync.getSynci(sync,GlSync.GL_SYNC_STATUS));
        GlSync.deleteSync(sync);GlProgram.deleteProgram(program);GlProgram.deleteShader(shader);
    }
}
