import java.nio.file.*;import java.util.*;import java.util.jar.*;import java.security.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.XaeroPixelCallers;import net.vulkanmod.gl.ShaderCallerRemapper;

/** Exact packaged caller transport, inverse bodies and meaningful negative controls. */
public final class PixelCallerAuditRegression {
 static void require(boolean condition,String name){if(!condition)throw new AssertionError(name);}
 static ClassNode read(byte[] b){ClassNode n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 static void reject(ClassNode node,String name){try{XaeroPixelCallers.apply(node);throw new AssertionError("Unsafe caller accepted: "+name);}catch(IllegalStateException expected){}}
 public static void main(String[] args)throws Exception{
  int controls=0,sites=0;
  String[][] names={{args[0],"xaero/map/graphics/PixelBuffers"},{args[1],"xaero/common/graphics/PixelBuffers"}};
  for(String[] row:names)try(JarFile jar=new JarFile(row[0])){
   byte[] raw=jar.getInputStream(jar.getJarEntry(row[1]+".class")).readAllBytes();ClassNode original=read(raw);String baseline=ShaderCallerRemapper.canonicalDigest(original);
   ClassNode repaired=read(raw);require(XaeroPixelCallers.apply(repaired)==8,"eight actual call owners");sites+=8;controls++;
   reject(repaired,"repeat rewrite");controls++;
   ClassNode unknown=read(raw);unknown.name="unreviewed/PixelBuffers";reject(unknown,"unknown name");controls++;
   ClassNode changed=read(raw);boolean mutated=false;for(var method:changed.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL15")){call.name="glUnreviewedOperation";mutated=true;break;}
   require(mutated,"negative code mutation existed");reject(changed,"changed original call body");controls++;
   int reverted=0;for(var method:repaired.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(XaeroPixelCallers.FACADE)){call.owner="org/lwjgl/opengl/GL15";reverted++;}
   require(reverted==8&&ShaderCallerRemapper.canonicalDigest(repaired).equals(baseline),"only exact original call owners change");controls++;
  }
  require(sites==16&&controls==10,"complete original and negative coverage");System.out.println("PIXEL_CALLER_AUDIT_PASS sites="+sites+" controls="+controls);
 }
}
