/* SPDX-License-Identifier: GPL-3.0-only */
import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Changes only four pinned call sites; coordinates, stack/flush logic and UI remain original. */
public final class PatchTRenderScissor {
    public static void main(String[]args)throws Exception {
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        if(Files.exists(output))throw new IllegalArgumentException("Use a fresh class output");
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(input)).accept(node,0);
        boolean cotton=node.name.equals("dev/tr7zw/trender/gui/client/CottonClientScreen");
        boolean scissors=node.name.equals("dev/tr7zw/trender/gui/client/Scissors");
        if(!cotton&&!scissors)throw new IllegalArgumentException("Unknown class");
        int enable=0,disable=0,box=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions) {
            if(!(instruction instanceof MethodInsnNode call)||!call.owner.startsWith("org/lwjgl/opengl/"))continue;
            if(call.getOpcode()!=Opcodes.INVOKESTATIC||!call.owner.equals("org/lwjgl/opengl/GL11"))throw new IllegalArgumentException("Unknown GL call");
            if(cotton&&method.name.equals("paint")&&call.desc.equals("(I)V")&&(call.name.equals("glEnable")||call.name.equals("glDisable"))) {
                AbstractInsnNode argument=call.getPrevious();
                while(argument!=null&&argument.getOpcode()<0)argument=argument.getPrevious();
                if(!(argument instanceof IntInsnNode value)||value.operand!=0x0C11)throw new IllegalArgumentException("Unknown capability");
                if(call.name.equals("glEnable"))enable++;else disable++;
            }else if(scissors&&method.name.equals("refreshScissors")&&call.name.equals("glScissor")&&call.desc.equals("(IIII)V"))box++;
            else throw new IllegalArgumentException("Unexpected scissor call site");
            call.owner="mvhtrendercompat/NativeScissorBridge";
        }
        if(cotton?(enable!=1||disable!=1||box!=0):(enable!=0||disable!=0||box!=2))throw new IllegalArgumentException("Pinned call count changed");
        ClassWriter writer=new ClassWriter(0);node.accept(writer);Files.write(output,writer.toByteArray());
        System.out.println("EXACT_TR_RENDER_SCISSOR_CALL_SITES_PATCHED "+(enable+disable+box));
    }
}
