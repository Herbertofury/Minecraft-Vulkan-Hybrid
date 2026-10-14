import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import net.vulkanmod.gl.*;

/** Exact selected calls, retained unimplemented methods, and inverse original body identity. */
public final class EssentialDeviceFramebufferCallerRegression {
    static ClassNode read(byte[] raw){var node=new ClassNode();new ClassReader(raw).accept(node,0);return node;}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reject(ClassNode node){try{EssentialDeviceFramebufferCallers.apply(node);throw new AssertionError("Unreviewed framebuffer accepted");}catch(IllegalStateException expected){}}
    public static void main(String[] args)throws Exception{int calls=0,controls=0;try(var jar=new JarFile(args[0])){for(String suffix:new String[]{"UGpuDeviceImpl","UGpuDeviceImpl$colorWriteFrameBuffer$2","UGpuDeviceImpl$depthWriteFrameBuffer$2"}){
        byte[] raw=jar.getInputStream(jar.getJarEntry("gg/essential/universal/render/"+suffix+".class")).readAllBytes();ClassNode original=read(raw),node=read(raw);String before=ShaderCallerRemapper.canonicalDigest(original);boolean root=suffix.equals("UGpuDeviceImpl");var result=EssentialDeviceFramebufferCallers.apply(node);require(result.framebufferCalls()==(root?26:suffix.contains("$color")?1:0)&&result.retainedRootGLCalls()==(root?21:0),"exact selected/retained call count");calls+=result.framebufferCalls();controls++;if(result.framebufferCalls()>0){reject(node);controls++;}
        ClassNode unknown=read(raw);unknown.name="unknown/Framebuffer";reject(unknown);controls++;ClassNode changed=read(raw);changed.methods.get(0).instructions.insertBefore(changed.methods.get(0).instructions.getFirst(),new InsnNode(Opcodes.NOP));reject(changed);controls++;
        int contexts=0;for(var method:node.methods)for(var instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call&&call.owner.equals(EssentialDeviceImageCallers.IMAGE)&&call.name.equals("configureTexture")){var getter=call.getPrevious();var usage=getter.getPrevious();var mips=usage.getPrevious();require(getter instanceof MethodInsnNode g&&g.name.equals("getBits")&&usage instanceof VarInsnNode u&&u.var==2&&mips instanceof VarInsnNode m&&m.var==6,"original mip/usage arguments preserved");method.instructions.remove(getter);method.instructions.remove(usage);method.instructions.remove(mips);contexts++;}
        require(contexts==(root?1:0),"only exact image context insertion");controls++;
        for(int m=0;m<node.methods.size();m++){var method=node.methods.get(m);var old=original.methods.get(m);List<MethodInsnNode>beforeCalls=new ArrayList<>(),afterCalls=new ArrayList<>();for(var instruction:old.instructions)if(instruction instanceof MethodInsnNode call)beforeCalls.add(call);for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call)afterCalls.add(call);require(beforeCalls.size()==afterCalls.size(),"original algorithm call count");for(int c=0;c<beforeCalls.size();c++){var a=beforeCalls.get(c);var b=afterCalls.get(c);if(b.owner.equals(EssentialDeviceFramebufferCallers.FACADE))require(Set.of("clearColor","clearDepth","glClear","genDepthOnlyFrameBuffer","invoke").contains(method.name),"framebuffer selection stays in reviewed methods");b.owner=a.owner;b.name=a.name;b.desc=a.desc;b.itf=a.itf;b.setOpcode(a.getOpcode());}}
        require(before.equals(ShaderCallerRemapper.canonicalDigest(node)),"all original methods/cleanup bytecode preserved by inverse");controls++;
    }}require(calls==27,"total selected calls");controls++;System.out.println("ESSENTIAL_FRAMEBUFFER_CALLER_PASS controls="+controls+" nativeSites="+calls+" inverseBodiesIdentical=true unimplementedRoot21Retained=true fullProviderAccepted=false");}
}
