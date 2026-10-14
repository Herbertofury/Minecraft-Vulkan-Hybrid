package net.vulkanmod.gl;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.Map;

/** Exact original fence wrappers keep their complete core/ARB fallback bodies.
 * Only the native branch bypasses GL capabilities, using real GPU sync objects. */
public final class EssentialSyncCallers {
    public static final String FACADE="net/vulkanmod/gl/NativeSyncApi";
    private static final String WRAPPERS="gg/essential/universal/render/UGpuFenceImplKt";
    private static final Map<String,String> PINS=Map.of(
        WRAPPERS,"8573cde3cfd5eae907283cc21902318e8053d04a69ecb91b883088a41a791f0b",
        "gg/essential/universal/render/UGpuFenceImpl","eac689a345371deb978ec91ea6c7ba1965af26d23a9b3e0e305992b6b3fe5cce");
    private EssentialSyncCallers(){}
    public static boolean target(String name){return PINS.containsKey(name.replace('.','/'));}
    public static int apply(ClassNode target){
        String pin=PINS.get(target.name);
        if(pin==null||!pin.equals(ShaderCallerRemapper.canonicalDigest(target)))
            throw new IllegalStateException("GPU fence caller requires reviewed original bytecode: "+target.name);
        int changed=0;
        if(target.name.equals(WRAPPERS)){
            for(MethodNode method:target.methods){
                String signature=method.name+method.desc;
                if(!signature.equals("glFenceSync(II)Ljava/lang/Long;")
                        &&!signature.equals("glDeleteSync(J)V")
                        &&!signature.equals("glClientWaitSync(JIJ)I"))continue;
                if((method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))!=(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))
                    throw new IllegalStateException("Fence wrapper ABI changed");
                LabelNode original=new LabelNode();InsnList branch=new InsnList();
                branch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/vulkanmod/compat/UniversalRendererGate","vulkanRendererEnabled","()Z",false));
                branch.add(new JumpInsnNode(Opcodes.IFEQ,original));
                if(method.name.equals("glFenceSync")){
                    branch.add(new VarInsnNode(Opcodes.ILOAD,0));branch.add(new VarInsnNode(Opcodes.ILOAD,1));
                    branch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,FACADE,"glFenceSync","(II)J",false));
                    branch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Long","valueOf","(J)Ljava/lang/Long;",false));
                    branch.add(new InsnNode(Opcodes.ARETURN));
                }else if(method.name.equals("glDeleteSync")){
                    branch.add(new VarInsnNode(Opcodes.LLOAD,0));
                    branch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,FACADE,"glDeleteSync","(J)V",false));
                    branch.add(new InsnNode(Opcodes.RETURN));
                }else{
                    branch.add(new VarInsnNode(Opcodes.LLOAD,0));branch.add(new VarInsnNode(Opcodes.ILOAD,2));branch.add(new VarInsnNode(Opcodes.LLOAD,3));
                    branch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,FACADE,"glClientWaitSync","(JIJ)I",false));
                    branch.add(new InsnNode(Opcodes.IRETURN));
                }
                branch.add(original);
                branch.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                method.instructions.insert(branch);method.maxStack=Math.max(method.maxStack,5);changed++;
            }
            if(changed!=3)throw new IllegalStateException("Fence wrappers incomplete");
        }else{
            for(MethodNode method:target.methods)for(AbstractInsnNode instruction:method.instructions)
                if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&!call.itf
                        &&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glGetError")&&call.desc.equals("()I")){
                    call.owner=FACADE;changed++;
                }
            if(changed!=1)throw new IllegalStateException("Fence failure error transport incomplete");
        }
        return changed;
    }
}
