package net.vulkanmod.compat;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import net.vulkanmod.gl.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class ShaderCallerAuditRegression {
    private static int controls;
    private static void require(boolean value,String reason){controls++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        Path artifact=Path.of(args[1]).toRealPath();
        for(Class<?> type:List.of(EssentialShaderCallerAudit.class,EssentialShaderCallers.class,ShaderCallerRemapper.class,NativeShaderApi.class))
            require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(artifact),"Packaged owner mismatch: "+type.getName());
        try(JarFile jar=new JarFile(args[0])){
            for(String simple:List.of("Shader","FloatUniform","IntUniform","Vec2Uniform","Vec4Uniform")){
                String entry="gg/essential/elementa/shaders/"+simple+".class";byte[] bytes;try(var input=jar.getInputStream(jar.getJarEntry(entry))){bytes=input.readAllBytes();}
                require(EssentialShaderCallerAudit.translated(entry,bytes),"Exact implemented caller rejected "+simple);
                byte[] changed=bytes.clone();changed[changed.length-1]^=1;require(!EssentialShaderCallerAudit.translated(entry,changed),"Changed original accepted "+simple);
                require(!EssentialShaderCallerAudit.translated("unreviewed/"+entry,bytes),"Unreviewed target name accepted");
                ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);node.methods.get(0).instructions.insert(new InsnNode(Opcodes.NOP));
                boolean blocked=false;try{EssentialShaderCallers.apply(node);}catch(IllegalStateException expected){blocked=true;}require(blocked,"Runtime original pin accepted changed body");
            }
        }
        ClassNode fixture=new ClassNode();fixture.version=Opcodes.V17;fixture.access=Opcodes.ACC_PUBLIC;fixture.name="CallSiteControls";fixture.superName="java/lang/Object";
        MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"fixture","()V",null,null);fixture.methods.add(method);
        MethodInsnNode known=new MethodInsnNode(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL20","glUniform1f","(IF)V",false);method.instructions.add(known);
        MethodInsnNode unknown=new MethodInsnNode(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL20","glUniform1fv","(ILjava/nio/FloatBuffer;)V",false);method.instructions.add(unknown);
        MethodInsnNode foreign=new MethodInsnNode(Opcodes.INVOKESTATIC,"unreviewed/GL20","glUniform1f","(IF)V",false);method.instructions.add(foreign);
        MethodInsnNode nonstatic=new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"org/lwjgl/opengl/GL20","glUniform1f","(IF)V",false);method.instructions.add(nonstatic);
        Handle handle=new Handle(Opcodes.H_INVOKESTATIC,"org/lwjgl/opengl/ARBShaderObjects","glUniform1fARB","(IF)V",false);
        Handle bootstrap=new Handle(Opcodes.H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory","()V",false);
        InvokeDynamicInsnNode lambda=new InvokeDynamicInsnNode("run","()Ljava/lang/Runnable;",bootstrap,Type.getMethodType("()V"),handle,Type.getMethodType("()V"));method.instructions.add(lambda);
        LdcInsnNode constant=new LdcInsnNode(new ConstantDynamic("shader","Ljava/lang/invoke/MethodHandle;",bootstrap,handle));method.instructions.add(constant);
        var result=ShaderCallerRemapper.apply(fixture);require(result.directCalls()==1&&result.methodHandles()==2,"Exact direct/handle coverage");
        require(known.owner.equals(ShaderCallerRemapper.FACADE),"Known caller was not repaired");
        require(unknown.owner.equals("org/lwjgl/opengl/GL20")&&foreign.owner.equals("unreviewed/GL20")&&nonstatic.owner.equals("org/lwjgl/opengl/GL20"),"Unknown descriptor/owner/nonstatic changed");
        require(lambda.bsm==bootstrap&&lambda.bsmArgs[0].equals(Type.getMethodType("()V"))&&((Handle)lambda.bsmArgs[1]).getOwner().equals(ShaderCallerRemapper.FACADE),"Lambda bootstrap/nonshader arguments changed");
        ConstantDynamic nested=(ConstantDynamic)constant.cst;require(nested.getBootstrapMethod()==bootstrap&&((Handle)nested.getBootstrapMethodArgument(0)).getOwner().equals(ShaderCallerRemapper.FACADE),"Nested method handle lost");
        var again=ShaderCallerRemapper.apply(fixture);require(again.directCalls()==0&&again.methodHandles()==0,"Second transport pass changed repaired bytecode");
        require(!ShaderCallerRemapper.supports("org/lwjgl/opengl/GL20","glUniform1fARB","(IF)V")&&!ShaderCallerRemapper.supports("org/lwjgl/opengl/ARBShaderObjects","glUniform1f","(IF)V"),"Cross-owner extension alias accepted");
        System.out.println("SHADER_CALLER_AUDIT_REGRESSION_PASS controls="+controls+" originalPins=5 changedPinsRejected=5 alteredRuntimeBodiesRejected=5 actualGpuOrGameProof=false");
    }
}
