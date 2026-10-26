package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.EssentialClipCallers;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;

/** Exact private original clipping executable; automatic Forge application is tested separately. */
final class OriginalEssentialClipProof implements AutoCloseable {
    private final JsonArray classes=new JsonArray();
    private final OriginalLoader loader;
    private int rewritten,activations,captures,stencilPhases;
    private static final String MIXIN="net.vulkanmod.mixin.compatibility.essential.EssentialClipCallerMixin";
    OriginalEssentialClipProof()throws Exception{
        Path root=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-original-inputs");
        Path jar=root.resolve("essential-original-private-reference.jar"),kotlin=root.resolve("kotlin-stdlib-2.3.0.jar");
        if(!hash(Files.readAllBytes(jar)).equals("1219f4cf3dfa874a9a0c0c45b2d48ada1b9a95a10b77ec75f1df60d3f9a27a06")||!hash(Files.readAllBytes(kotlin)).equals("887587c91713250ad52fe14ad9166d042c33835049890e9437f355ffc5a195b1"))throw new IllegalStateException("Original clipping inputs changed");
        loader=new OriginalLoader(jar,kotlin);
        for(String name:targets())Class.forName(name,false,loader);
    }
    static String[] targets(){return new String[]{"gg.essential.universal.render.ScissorState","gg.essential.universal.render.ScissorState$Companion","gg.essential.util.ScissorState","gg.essential.util.ScissorState$Companion","gg.essential.elementa.effects.StencilEffect"};}
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static Object invoke(Method method,Object receiver,Object...args)throws Exception{
        try{return method.invoke(receiver,args);}catch(InvocationTargetException error){if(error.getCause() instanceof Exception cause)throw cause;if(error.getCause() instanceof Error cause)throw cause;throw error;}
    }
    Object state(String name,boolean enabled,int x,int y,int width,int height)throws Exception{return Class.forName(name,true,loader).getConstructor(boolean.class,int.class,int.class,int.class,int.class).newInstance(enabled,x,y,width,height);}
    Object coerce(Object state,int x,int y,int width,int height)throws Exception{return invoke(state.getClass().getMethod("coerceIn",int.class,int.class,int.class,int.class),state,x,y,width,height);}
    void activate(Object state)throws Exception{invoke(state.getClass().getMethod("activate"),state);activations++;}
    void capture(String name,boolean enabled,int x,int y,int width,int height)throws Exception{
        Class<?> type=Class.forName(name,true,loader);Object companion=type.getField("Companion").get(null),actual=invoke(companion.getClass().getMethod("active"),companion);
        if((boolean)invoke(type.getMethod("getEnabled"),actual)!=enabled)throw new IllegalStateException("Original enabled capture changed");
        int[] expected={x,y,width,height};String[] getter={"getX","getY","getWidth","getHeight"};
        for(int n=0;n<4;n++)if((int)invoke(type.getMethod(getter[n]),actual)!=expected[n])throw new IllegalStateException("Original box capture changed");captures++;
    }
    Object stencil()throws Exception{return Class.forName("gg.essential.elementa.effects.StencilEffect",true,loader).getConstructor().newInstance();}
    Object matrix()throws Exception{return Class.forName("gg.essential.universal.UMatrixStack",true,loader).getConstructor().newInstance();}
    void stencil(Object effect,Object matrix,String phase)throws Exception{invoke(effect.getClass().getMethod(phase,matrix.getClass()),effect,matrix);stencilPhases++;}
    void complete()throws Exception{
        if(rewritten!=16||activations!=12||captures!=18||stencilPhases!=3)throw new IllegalStateException("Original clip executable coverage incomplete: "+rewritten+" / "+activations+" / "+captures+" / "+stencilPhases);
        JsonObject report=new JsonObject();report.addProperty("accepted",true);report.addProperty("rewritten_sites",rewritten);report.addProperty("original_state_activations",activations);report.addProperty("original_state_captures",captures);report.addProperty("original_stencil_phases",stencilPhases);report.addProperty("automatic_forge_application_proven",false);report.addProperty("full_essential_features_proven",false);report.add("classes",classes);
        Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-essential-original-clip-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    public void close()throws Exception{loader.close();}
    private final class OriginalLoader extends URLClassLoader{
        private final JarFile jar,kotlin;
        OriginalLoader(Path original,Path stdlib)throws Exception{super(new URL[]{original.toUri().toURL(),stdlib.toUri().toURL()},OriginalEssentialClipProof.class.getClassLoader());jar=new JarFile(original.toFile());kotlin=new JarFile(stdlib.toFile());}
        protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            boolean own=name.startsWith("gg.essential.elementa.")||name.startsWith("gg.essential.universal.")||name.startsWith("gg.essential.util.ScissorState")||name.startsWith("kotlin.");
            if(!own)return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;}
        }
        protected Class<?> findClass(String name)throws ClassNotFoundException{
            try{
                JarFile owner=name.startsWith("kotlin.")?kotlin:jar;var entry=owner.getJarEntry(name.replace('.','/')+".class");if(entry==null)throw new ClassNotFoundException("Missing exact original dependency "+name);
                byte[] bytes;try(var stream=owner.getInputStream(entry)){bytes=stream.readAllBytes();}
                if(EssentialClipCallers.target(name)){
                    String before=hash(bytes);ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
                    new net.vulkanmod.mixin.MixinPlugin().preApply(name,node,MIXIN,null);
                    int count=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(EssentialClipCallers.FACADE))count++;
                    rewritten+=count;ClassWriter output=new ClassWriter(0);node.accept(output);bytes=output.toByteArray();JsonObject row=new JsonObject();row.addProperty("class",name);row.addProperty("original_sha256",before);row.addProperty("rewritten_sites",count);row.addProperty("transformed_sha256",hash(bytes));classes.add(row);
                }
                return defineClass(name,bytes,0,bytes.length);
            }catch(ClassNotFoundException error){throw error;}catch(Exception error){throw new ClassNotFoundException("Original clip class loading failed "+name,error);}
        }
        public void close()throws java.io.IOException{try{jar.close();}finally{try{kotlin.close();}finally{super.close();}}}
    }
}
