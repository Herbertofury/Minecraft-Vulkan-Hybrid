/* SPDX-License-Identifier: LGPL-3.0-only */
import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Changes exactly one owner; descriptor, operation and vertex data stay intact. */
public final class PatchMemoryOwner {
    public static void main(String[] args)throws Exception {
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        if(Files.exists(output))throw new IllegalArgumentException("Preserve prior patched class");
        byte[] bytes=Files.readAllBytes(input);
        if(bytes.length>1_000_000)throw new IllegalArgumentException("Unexpected class size");
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!node.name.equals("net/irisshaders/batchedentityrendering/mixin/MixinBufferBuilder_SegmentRendering"))throw new IllegalArgumentException("Unexpected target class");
        int count=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call&&call.owner.equals("net/caffeinemc/mods/sodium/api/memory/MemoryIntrinsics")) {
                if(call.getOpcode()!=Opcodes.INVOKESTATIC||!call.name.equals("copyMemory")||!call.desc.equals("(JJI)V"))throw new IllegalArgumentException("Unknown public memory API call");
                call.owner="mvhoculuscompat/MemoryCopy";count++;
            }
        if(count!=1)throw new IllegalArgumentException("Expected one pinned memory-copy call, got "+count);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);Files.write(output,writer.toByteArray());
        System.out.println("OCULUS_SINGLE_MEMORY_OWNER_PATCH_PASS");
    }
}
