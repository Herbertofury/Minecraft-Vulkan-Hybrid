package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Automatic Forge/Mixin call-site proof; raw private originals are loaded without initialization. */
final class OriginalEssentialForgeApplyProof {
    static void run()throws Exception {
        Path root=Minecraft.getInstance().gameDirectory.toPath();JsonArray rows=new JsonArray();int total=0;
        for(String simple:new String[]{"Shader","FloatUniform","IntUniform","Vec2Uniform","Vec4Uniform"}){
            String name="gg.essential.elementa.shaders."+simple;Class<?> actual=Class.forName(name,false,OriginalEssentialForgeApplyProof.class.getClassLoader());
            if(!actual.getClassLoader().getClass().getName().equals("cpw.mods.modlauncher.TransformingClassLoader")||!net.minecraftforge.fml.loading.FMLLoader.getGameLayer().modules().contains(actual.getModule()))throw new IllegalStateException("Original caller did not load in Forge GAME: "+name);
            Path file=root.resolve(".mixin.out/class/"+name.replace('.','/')+".class");
            if(!Files.isRegularFile(file)||Files.getLastModifiedTime(file).toMillis()<ManagementFactory.getRuntimeMXBean().getStartTime())throw new IllegalStateException("Fresh automatic original caller export missing: "+name);
            byte[] bytes=Files.readAllBytes(file);ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int transformed=0,untranslated=0;
            for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call){if(call.owner.equals(ShaderCallerRemapper.FACADE))transformed++;else if(ShaderCallerRemapper.supports(call.owner,call.name,call.desc))untranslated++;}
            int expected=simple.equals("Shader")?16:2;if(transformed!=expected||untranslated!=0)throw new IllegalStateException("Automatic original call sites incomplete: "+name+" "+transformed+" / "+untranslated);
            JsonObject row=new JsonObject();row.addProperty("class",name);row.addProperty("class_loader",actual.getClassLoader().getClass().getName());row.addProperty("module",actual.getModule().getName());row.addProperty("rewritten_calls",transformed);row.addProperty("untranslated_supported_calls",untranslated);row.addProperty("fresh_export_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));row.addProperty("class_initialized_or_original_methods_invoked",false);rows.add(row);total+=transformed;
        }
        if(total!=24)throw new IllegalStateException("Automatic original caller count mismatch");
        JsonObject report=new JsonObject();report.addProperty("accepted",true);report.addProperty("automatic_forge_application_proven",true);report.addProperty("rewritten_calls",total);report.addProperty("scope","Exact private raw original 5 caller classes automatically transformed by the production Forge plugin in GAME, loaded without initialization; full Essential mod/features/pack remain separate. Exact original executable native pixels use a separate controlled loader.");report.add("classes",rows);Files.writeString(root.resolve("mvh-essential-forge-apply-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
