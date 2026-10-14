import net.vulkanmod.gl.ShaderCallerRemapper;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.zip.*;
import java.nio.file.*;
import java.security.*;

/** Exact original caller bytecode/ABI review; no provider execution or FPS claim. */
public final class ShaderCallerOriginalReview {
    public static void main(String[] args)throws Exception {
        int total=0;List<String> lines=new ArrayList<>();
        try(ZipFile jar=new ZipFile(args[0])) {
            for(String name:new String[]{"FloatUniform","IntUniform","Shader","Vec2Uniform","Vec4Uniform"}) {
                String path="gg/essential/elementa/shaders/"+name+".class";byte[] bytes;
                try(var stream=jar.getInputStream(jar.getEntry(path))){bytes=stream.readAllBytes();}
                ClassNode target=new ClassNode();new ClassReader(bytes).accept(target,0);
                String pin=ShaderCallerRemapper.canonicalDigest(target);int before=0;
                for(MethodNode method:target.methods)for(AbstractInsnNode instruction:method.instructions)
                    if(instruction instanceof MethodInsnNode call && call.owner.startsWith("org/lwjgl/opengl/") && (call.owner.endsWith("GL20") || call.owner.endsWith("GL20C") || call.owner.endsWith("ARBShaderObjects"))) {
                        if(!ShaderCallerRemapper.supports(call.owner,call.name,call.desc))throw new AssertionError("Unimplemented original shader ABI: "+call.owner+"#"+call.name+call.desc);
                        before++;
                    }
                var result=ShaderCallerRemapper.apply(target);
                if(before!=result.directCalls() || result.directCalls()==0)throw new AssertionError("Original shader calls omitted: "+name);
                if(ShaderCallerRemapper.apply(target).directCalls()!=0)throw new AssertionError("Caller transformation not idempotent");
                ClassWriter writer=new ClassWriter(0);target.accept(writer);byte[] transformed=writer.toByteArray();
                Path out=Path.of(args[1]).resolve(path);Files.createDirectories(out.getParent());Files.write(out,transformed);
                String raw=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                lines.add(name+" "+raw+" "+pin+" "+result.directCalls()+" "+result.methodHandles());total+=result.directCalls();
            }
        }
        for(String line:lines)System.out.println(line);
        System.out.println("EXACT_ORIGINAL_SHADER_CALLSITE_ABI_REVIEW_PASS "+total+" direct calls; original executable not run");
    }
}
