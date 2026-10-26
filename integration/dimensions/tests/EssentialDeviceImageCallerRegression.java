import java.util.*;import java.util.jar.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import net.vulkanmod.gl.*;
/** Exact original image paths, preserved other methods and reversible mip context transport. */
public final class EssentialDeviceImageCallerRegression {
 static ClassNode read(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void reject(ClassNode n){try{EssentialDeviceImageCallers.apply(n);throw new AssertionError("Unsafe typed image accepted");}catch(IllegalStateException expected){}}
 public static void main(String[] args)throws Exception{int sites=0,contexts=0,controls=0;try(var jar=new JarFile(args[0])){for(String suffix:new String[]{"UGpuDeviceImpl","UGpuTextureImpl","UGpuDeviceImpl$info$1$limits$1"}){
  byte[] raw=jar.getInputStream(jar.getJarEntry("gg/essential/universal/render/"+suffix+".class")).readAllBytes();var n=read(raw);String before=ShaderCallerRemapper.canonicalDigest(n);boolean root=suffix.equals("UGpuDeviceImpl");List<String> owners=new ArrayList<>();if(root)for(var m:n.methods)if(Set.of("createBuffer","mapBuffer","withBufferBound").contains(m.name))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.startsWith("org/lwjgl/opengl/"))owners.add(c.owner);var result=EssentialDeviceImageCallers.apply(n);require(result.imageCalls()==(root?3:suffix.equals("UGpuTextureImpl")?1:3)&&result.retainedRootGLCalls()==(root?45:0),"exact image scope");controls++;sites+=result.imageCalls();contexts+=result.addedMipContext();reject(n);controls++;var unknown=read(raw);unknown.name="unknown/Image";reject(unknown);controls++;var bad=read(raw);boolean changed=false;for(var m:bad.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c&&(c.owner.startsWith("org/lwjgl/opengl/")||c.owner.startsWith("com/mojang/blaze3d/"))){c.name="unknownImageOperation";changed=true;}require(changed,"real method mutation");reject(bad);controls++;
  int cursor=0,removedContexts=0;for(var m:n.methods)for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c){
   if(c.owner.equals(EssentialDeviceBufferCallers.BUFFER)||c.owner.equals(EssentialDeviceBufferCallers.VERTEX))c.owner=owners.get(cursor++);
   else if(c.owner.equals(EssentialDeviceImageCallers.IMAGE)){
    if(c.name.equals("glGenTextures")){c.owner="com/mojang/blaze3d/platform/GlStateManager";c.name="_genTexture";}
    else if(c.name.equals("configureTexture")){var getter=c.getPrevious();var argument=getter.getPrevious();var mip=argument.getPrevious();require(getter instanceof MethodInsnNode g&&g.owner.equals("gg/essential/universal/render/UGpuTexture$Usage")&&g.name.equals("getBits")&&argument instanceof VarInsnNode a&&a.getOpcode()==Opcodes.ALOAD&&a.var==2&&mip instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==6,"actual requested mip/usage context");m.instructions.remove(getter);m.instructions.remove(argument);m.instructions.remove(mip);c.owner="gg/essential/universal/UGraphics";c.desc="(ILjava/lang/Runnable;)V";removedContexts++;}
    else if(c.name.equals("glDeleteTextures")){c.owner="com/mojang/blaze3d/systems/RenderSystem";c.name="deleteTexture";}
    else c.owner="org/lwjgl/opengl/GL11";
   }
  }
  require(cursor==owners.size()&&removedContexts==(root?1:0)&&ShaderCallerRemapper.canonicalDigest(n).equals(before),"whole original inverse including unimplemented framebuffers");controls++;
 }}require(sites==7&&contexts==1,"totals");controls++;System.out.println("ESSENTIAL_IMAGE_CALLER_REGRESSION_PASS controls="+controls+" selectedSites="+sites+" realMipContexts="+contexts+" inverseBodiesIdentical=true fullProviderAccepted=false");}
}
