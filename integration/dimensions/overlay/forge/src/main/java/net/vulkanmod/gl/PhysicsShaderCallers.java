package net.vulkanmod.gl;
import org.objectweb.asm.tree.ClassNode;

/** One reviewed original Shader helper; buffers/draws/context and full Physics remain separate. */
public final class PhysicsShaderCallers {
    private static final String NAME="net/diebuddies/opengl/Shader";
    private static final String PIN="422f75bc40bc66c918b2678af84fae82e2922e9ffbd21659a19e6fb4d6b409ed";
    private PhysicsShaderCallers(){}
    public static boolean target(String name){return NAME.equals(name.replace('.','/'));}
    public static ShaderCallerRemapper.Result apply(ClassNode node){
        if(!NAME.equals(node.name) || !PIN.equals(ShaderCallerRemapper.canonicalDigest(node)))throw new IllegalStateException("Physics shader caller requires exact reviewed original body");
        var result=ShaderCallerRemapper.apply(node);
        if(result.directCalls()!=52 || result.methodHandles()!=0)throw new IllegalStateException("Original Physics shader transport incomplete");
        return result;
    }
}
