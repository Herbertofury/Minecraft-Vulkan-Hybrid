package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.EssentialClipCallers;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;

/** Actual automatic Forge application on private exact originals, without initialization. */
final class OriginalEssentialClipForgeApplyProof {
    static void run()throws Exception{
        Path root=Minecraft.getInstance().gameDirectory.toPath();JsonArray rows=new JsonArray();int total=0;
        for(String name:OriginalEssentialClipProof.targets()){
            Class<?> type=Class.forName(name,false,OriginalEssentialClipForgeApplyProof.class.getClassLoader());
            if(!type.getClassLoader().getClass().getName().equals("cpw.mods.modlauncher.TransformingClassLoader")||!net.minecraftforge.fml.loading.FMLLoader.getGameLayer().modules().contains(type.getModule()))throw new IllegalStateException("Original clip caller outside GAME "+name);
            Path file=root.resolve(".mixin.out/class/"+name.replace('.','/')+".class");if(!Files.isRegularFile(file)||Files.getLastModifiedTime(file).toMillis()<ManagementFactory.getRuntimeMXBean().getStartTime())throw new IllegalStateException("Fresh automatic clipping export absent "+name);
            byte[] bytes=Files.readAllBytes(file);ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int translated=0,remaining=0;
            for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){if(call.owner.equals(EssentialClipCallers.FACADE))translated++;else if(EssentialClipCallers.supports(call.owner,call.name,call.desc))remaining++;}
            int expected=name.endsWith("StencilEffect")?6:name.endsWith("$Companion")?2:3;if(translated!=expected||remaining!=0)throw new IllegalStateException("Automatic clipping caller sites differ "+name+": "+translated+" / "+remaining);total+=translated;
            JsonObject row=new JsonObject();row.addProperty("class",name);row.addProperty("class_loader",type.getClassLoader().getClass().getName());row.addProperty("module",type.getModule().getName());row.addProperty("rewritten_sites",translated);row.addProperty("untranslated_supported_sites",remaining);row.addProperty("fresh_export_sha256",OriginalEssentialClipProof.hash(bytes));row.addProperty("class_initialized_or_original_methods_invoked",false);rows.add(row);
        }
        if(total!=16)throw new IllegalStateException("Clipping transformation total differs");JsonObject report=new JsonObject();report.addProperty("accepted",true);report.addProperty("automatic_forge_application_proven",true);report.addProperty("rewritten_sites",total);report.addProperty("full_essential_features_proven",false);report.add("classes",rows);Files.writeString(root.resolve("mvh-essential-clip-forge-apply-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
