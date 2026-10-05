/* SPDX-License-Identifier: GPL-3.0-only */
import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Only four literal GL_STENCIL_TEST call instructions change owner; all Ponder logic remains. */
public final class PatchPonderStencil {
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]),output=Path.of(args[1]);if(Files.exists(output))throw new IllegalArgumentException("Use fresh output");
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(input)).accept(node,0);
        if(!node.name.equals("net/createmod/catnip/gui/element/StencilElement"))throw new IllegalArgumentException("Wrong class");
        int enable=0,disable=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode item:method.instructions){
            if(!(item instanceof MethodInsnNode call)||!call.owner.startsWith("org/lwjgl/opengl/"))continue;
            if(call.getOpcode()!=Opcodes.INVOKESTATIC||!call.owner.equals("org/lwjgl/opengl/GL11")||!call.desc.equals("(I)V"))throw new IllegalArgumentException("Unknown GL call");
            AbstractInsnNode argument=call.getPrevious();while(argument!=null&&argument.getOpcode()<0)argument=argument.getPrevious();
            if(!(argument instanceof IntInsnNode value)||value.operand!=2960)throw new IllegalArgumentException("Unknown capability");
            boolean validEnable=call.name.equals("glEnable")&&(method.name.equals("prepareStencil")||method.name.equals("prepareElement"));
            boolean validDisable=call.name.equals("glDisable")&&(method.name.equals("prepareStencil")||method.name.equals("cleanUp"));
            if(validEnable)enable++;else if(validDisable)disable++;else throw new IllegalArgumentException("Unknown call site");
            call.owner="mvhpondercompat/NativeStencilBridge";
        }
        if(enable!=2||disable!=2)throw new IllegalArgumentException("Pinned call count changed");
        ClassWriter writer=new ClassWriter(0);node.accept(writer);Files.write(output,writer.toByteArray());System.out.println("PONDER_EXACT_STENCIL_INSTRUCTIONS_PATCHED 4");
    }
}
