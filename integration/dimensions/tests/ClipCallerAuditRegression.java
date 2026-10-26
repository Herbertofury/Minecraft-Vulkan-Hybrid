import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.*;

/** Exact packaged original-body and preflight pins; no driver or FPS claim. */
public final class ClipCallerAuditRegression {
    private static int controls;
    private static void check(boolean result){if(!result)throw new AssertionError("Control "+controls+" failed");controls++;}
    public static void main(String[] args)throws Exception{
        Path production=Path.of(args[0]).toRealPath();
        Class<?> audit=Class.forName("net.vulkanmod.compat.EssentialClipCallerAudit");var translated=audit.getDeclaredMethod("translated",String.class,byte[].class);translated.setAccessible(true);
        for(Class<?> type:new Class<?>[]{EssentialClipCallers.class,NativeClipApi.class,audit})check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(production));
        String[] names={"gg.essential.universal.render.ScissorState","gg.essential.universal.render.ScissorState$Companion","gg.essential.util.ScissorState","gg.essential.util.ScissorState$Companion","gg.essential.elementa.effects.StencilEffect"};int total=0;
        try(JarFile original=new JarFile(args[1])){for(String name:names){
            String entry=name.replace('.','/')+".class";byte[] raw=original.getInputStream(original.getJarEntry(entry)).readAllBytes();check((boolean)translated.invoke(null,entry,raw));byte[] mutated=raw.clone();mutated[mutated.length-1]^=1;check(!(boolean)translated.invoke(null,entry,mutated));check(!(boolean)translated.invoke(null,"unreviewed/"+entry,raw));
            ClassNode node=new ClassNode();new ClassReader(raw).accept(node,0);ClassNode changed=new ClassNode();new ClassReader(raw).accept(changed,0);changed.methods.get(0).instructions.insert(new InsnNode(Opcodes.NOP));boolean refused=false;try{EssentialClipCallers.apply(changed);}catch(IllegalStateException expected){refused=true;}check(refused);
            int expected=name.endsWith("StencilEffect")?6:name.endsWith("$Companion")?2:3;check(EssentialClipCallers.apply(node)==expected);int rewritten=0,remaining=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){if(call.owner.equals(EssentialClipCallers.FACADE))rewritten++;else if(EssentialClipCallers.supports(call.owner,call.name,call.desc))remaining++;}check(rewritten==expected&&remaining==0);total+=rewritten;
            refused=false;try{EssentialClipCallers.apply(node);}catch(IllegalStateException expectedError){refused=true;}check(refused);
        }}
        check(total==16);check(!EssentialClipCallers.supports("unreviewed/GL11","glEnable","(I)V"));check(!EssentialClipCallers.supports("org/lwjgl/opengl/GL11","glDrawArrays","(III)V"));check(!EssentialClipCallers.supports("org/lwjgl/opengl/GL11","glScissor","(JJJJ)V"));check(!EssentialClipCallers.target("gg.essential.universal.UGraphics"));
        for(var method:NativeClipApi.class.getDeclaredMethods())if(java.lang.reflect.Modifier.isPublic(method.getModifiers()))check(EssentialClipCallers.supports("org/lwjgl/opengl/GL11C",method.getName(),Type.getMethodDescriptor(method)));
        System.out.println("PASS_PACKAGED_CLIP_PINS_AND_ABI_CONTROLS="+controls+" ORIGINAL_CALLS="+total+" NO_GENERIC_PROVIDER_OR_API_EXEMPTION");
    }
}
