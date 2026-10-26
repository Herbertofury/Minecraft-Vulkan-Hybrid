package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;

/** Private exact-original executable component proof. It does not claim automatic Forge application. */
final class OriginalElementaAtlasProof implements AutoCloseable {
    private static final String PREFIX="gg.essential.elementa.shaders.";
    private static final String JAR_SHA="1219f4cf3dfa874a9a0c0c45b2d48ada1b9a95a10b77ec75f1df60d3f9a27a06";
    private static final String KOTLIN_SHA="887587c91713250ad52fe14ad9166d042c33835049890e9437f355ffc5a195b1";
    private static final Map<String,String> PINS=Map.of(
        PREFIX+"FloatUniform","50acc9988f6c6e886d3a2ae5ed7768042826e9b7236eea8660f50f18419471b5",
        PREFIX+"IntUniform","23a3cc8f07c538dd525bd7656ccb55c82597c5479acf56ea328c1dd1324e72fa",
        PREFIX+"Shader","015b7d0967892e0a26809b91954375c00639dcc4725d9557424540278af6701e",
        PREFIX+"Vec2Uniform","f94816f3128bc0ff028e29dacd660fb65d399b32ae82ac51809d2d89483336da",
        PREFIX+"Vec4Uniform","313d30f932d18a205e753ad48b4a5e4eddff37296c8f63dda8f5cd0b002510fb");
    private final JsonArray trace=new JsonArray();
    private final Path report=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-atlas-original-proof.json");
    private final OriginalLoader loader;
    private final Class<?> shaderClass;
    private Object shader;
    private int programs,uniforms;
    OriginalElementaAtlasProof() throws Exception {
        Path path=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-original-inputs/essential-original-private-reference.jar");
        Path kotlin=path.resolveSibling("kotlin-stdlib-2.3.0.jar");
        if(!hash(Files.readAllBytes(path)).equals(JAR_SHA))throw new IllegalStateException("Original Essential reference changed");
        if(!hash(Files.readAllBytes(kotlin)).equals(KOTLIN_SHA))throw new IllegalStateException("Original Essential Kotlin dependency changed");
        loader=new OriginalLoader(path,kotlin);
        // Resolve all five pins, including both original uniform branches, before invoking originals.
        for(String name:new TreeSet<>(PINS.keySet()))Class.forName(name,false,loader);
        shaderClass=Class.forName(PREFIX+"Shader",false,loader);
        save(false);
    }
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static Object invoke(Method method,Object receiver,Object...args)throws Exception {
        try{return method.invoke(receiver,args);}catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof Exception error)throw error;if(cause instanceof Error error)throw error;throw failure;
        }
    }
    int compile(String kind)throws Exception {
        try{shader=shaderClass.getConstructor(String.class,String.class).newInstance(kind.equals("font")?"font":"rect",kind);}
        catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof Exception error)throw error;if(cause instanceof Error error)throw error;throw failure;}
        if(!(boolean)invoke(shaderClass.getMethod("getUsable"),shader))throw new IllegalStateException("Original Essential shader was unusable: "+kind);
        Method program=shaderClass.getDeclaredMethod("getProgram");program.setAccessible(true);int id=(int)invoke(program,shader);
        if(!GlProgram.isProgram(id)||GlProgram.getProgrami(id,0x8B82)!=1||GlProgram.getProgrami(id,0x8B83)!=1)throw new IllegalStateException("Original shader did not produce a real native validated program");
        invoke(shaderClass.getMethod("bindIfUsable"),shader);if(GlProgram.currentProgramId()!=id)throw new IllegalStateException("Original bind missed native kernel");
        programs++;JsonObject row=new JsonObject();row.addProperty("phase","original_program_bound");row.addProperty("kind",kind);row.addProperty("program",id);trace.add(row);save(false);return id;
    }
    void uniform(String name,float...values)throws Exception {
        int location=(int)invoke(shaderClass.getMethod("getUniformLocation",String.class),shader,name);
        if(location<0)throw new IllegalStateException("Original uniform missing: "+name);
        String simple=values.length==1?"FloatUniform":values.length==2?"Vec2Uniform":values.length==4?"Vec4Uniform":"";
        if(simple.isEmpty())throw new IllegalArgumentException("Unreviewed original uniform arity");
        Class<?> type=Class.forName(PREFIX+simple,true,loader);Object uniform=type.getConstructor(int.class).newInstance(location);
        if(values.length==1)invoke(type.getMethod("setValue",float.class),uniform,values[0]);
        else{Class<?> vector=Class.forName("gg.essential.elementa.utils.Vector"+values.length+"f",true,loader);Class<?>[] types=new Class<?>[values.length];Arrays.fill(types,float.class);Object[] args=new Object[values.length];for(int n=0;n<values.length;n++)args[n]=values[n];Object value=vector.getConstructor(types).newInstance(args);invoke(type.getMethod("setValue",vector),uniform,value);}
        uniforms++;save(false);
    }
    void integer(String name,int value)throws Exception {
        int location=(int)invoke(shaderClass.getMethod("getUniformLocation",String.class),shader,name);
        if(location<0)throw new IllegalStateException("Original integer uniform missing: "+name);
        Class<?> type=Class.forName(PREFIX+"IntUniform",true,loader);Object uniform=type.getConstructor(int.class).newInstance(location);invoke(type.getMethod("setValue",int.class),uniform,value);uniforms++;save(false);
    }
    void unbind()throws Exception{invoke(shaderClass.getMethod("unbindIfUsable"),shader);if(GlProgram.currentProgramId()!=0)throw new IllegalStateException("Original unbind missed kernel");}
    void complete()throws Exception{if(programs!=3||uniforms!=15||loader.rewritten!=24)throw new IllegalStateException("Original executable coverage incomplete: "+programs+" / "+uniforms+" / "+loader.rewritten);save(true);}
    private void save(boolean accepted)throws Exception {
        JsonObject data=new JsonObject();data.addProperty("accepted",accepted);data.addProperty("original_jar_sha256",JAR_SHA);data.addProperty("original_programs",programs);data.addProperty("original_uniform_updates",uniforms);data.addProperty("rewritten_call_sites",loader.rewritten);data.addProperty("automatic_forge_application_proven",false);data.addProperty("original_executable_native_component_only",true);data.add("trace",trace);Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }
    @Override public void close()throws Exception{loader.close();}
    private final class OriginalLoader extends URLClassLoader {
        private final JarFile jar,kotlin;int rewritten;
        OriginalLoader(Path path,Path stdlib)throws Exception{super(new URL[]{path.toUri().toURL(),stdlib.toUri().toURL()},OriginalElementaAtlasProof.class.getClassLoader());jar=new JarFile(path.toFile());kotlin=new JarFile(stdlib.toFile());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            boolean original=name.startsWith("gg.essential.elementa.shaders.")||name.startsWith("gg.essential.elementa.utils.")||name.startsWith("gg.essential.universal.")||name.startsWith("kotlin.");
            if(!original)return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){Class<?> result=findLoadedClass(name);if(result==null)result=findClass(name);if(resolve)resolveClass(result);return result;}
        }
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            try{
                JarFile owner=name.startsWith("kotlin.")?kotlin:jar;
                var entry=owner.getJarEntry(name.replace('.','/')+".class");if(entry==null)throw new ClassNotFoundException("Exact original dependency unavailable: "+name);
                byte[] bytes;try(var stream=owner.getInputStream(entry)){bytes=stream.readAllBytes();}
                String digest=hash(bytes);JsonObject row=new JsonObject();row.addProperty("phase","original_class_define");row.addProperty("class",name);row.addProperty("original_sha256",digest);
                if(PINS.containsKey(name)){
                    if(!PINS.get(name).equals(digest))throw new IllegalStateException("Original class changed: "+name);
                    ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
                    // Execute the same packaged production plugin callback, with real original bytecode.
                    new net.vulkanmod.mixin.MixinPlugin().preApply(name,node,"net.vulkanmod.mixin.compatibility.essential.EssentialShaderCallerMixin",null);
                    int count=0;for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(ShaderCallerRemapper.FACADE))count++;
                    if(count==0)throw new IllegalStateException("Production callback omitted caller: "+name);rewritten+=count;
                    ClassWriter output=new ClassWriter(0);node.accept(output);bytes=output.toByteArray();row.addProperty("rewritten_sites",count);row.addProperty("transformed_sha256",hash(bytes));
                }
                trace.add(row);save(false);return defineClass(name,bytes,0,bytes.length);
            }catch(ClassNotFoundException failure){throw failure;}catch(Exception failure){throw new ClassNotFoundException("Original executable component loading failed: "+name,failure);}
        }
        @Override public void close()throws java.io.IOException{try{jar.close();}finally{try{kotlin.close();}finally{super.close();}}}
    }
}
