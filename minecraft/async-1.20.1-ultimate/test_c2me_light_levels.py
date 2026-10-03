#!/usr/bin/env python3
"""Execute real ASM against native-causal LIGHT ticket fixtures; full native QA also required."""
import argparse, hashlib, json, subprocess, tempfile, urllib.request
from pathlib import Path

FILES = {
"net/minecraft/world/level/chunk/ChunkStatus.java": """package net.minecraft.world.level.chunk;
public class ChunkStatus { public static final ChunkStatus LIGHT=new ChunkStatus(), FEATURES=new ChunkStatus();
public static final ChunkStatus f_62323_=LIGHT, f_62315_=FEATURES; }""",
"net/minecraft/world/level/ChunkPos.java": """package net.minecraft.world.level; public record ChunkPos(int x,int z) {}""",
"net/minecraft/server/level/ChunkLevel.java": """package net.minecraft.server.level;
import net.minecraft.world.level.chunk.ChunkStatus;
public class ChunkLevel { public static int byStatus(ChunkStatus status){return status==ChunkStatus.LIGHT?34:33;}
public static int m_287141_(ChunkStatus status){return byStatus(status);} }""",
"net/minecraft/server/level/TicketType.java": """package net.minecraft.server.level; public class TicketType {
public static final TicketType LIGHT=new TicketType(), f_9446_=LIGHT; }""",
"net/minecraft/server/level/DistanceManager.java": """package net.minecraft.server.level;
import java.util.*;import net.minecraft.world.level.ChunkPos;
public class DistanceManager {
public record Ticket(TicketType type,ChunkPos position,int level,Object argument){}
public Set<Ticket> tickets=new HashSet<>();public int removals;public RuntimeException failure;
public void addTicket(TicketType type,ChunkPos pos,int level,Object arg){tickets.add(new Ticket(type,pos,level,arg));}
public void removeTicket(TicketType type,ChunkPos pos,int level,Object arg){removals++;if(failure!=null)throw failure;tickets.remove(new Ticket(type,pos,level,arg));}
public void m_140823_(TicketType type,ChunkPos pos,int level,Object arg){removeTicket(type,pos,level,arg);}
}""",
"net/minecraft/server/level/ChunkMap.java": """package net.minecraft.server.level;
import java.util.*;import net.minecraft.world.level.ChunkPos;import net.minecraft.world.level.chunk.ChunkStatus;
public class ChunkMap {
public final DistanceManager manager=new DistanceManager();public final List<Runnable> ownerQueue=new ArrayList<>();
private int redirect$zie000$redirectAddLightTicketDistance(ChunkStatus status){
return status==ChunkStatus.LIGHT?ChunkLevel.byStatus(ChunkStatus.FEATURES)-2:ChunkLevel.byStatus(status);}
public void add(ChunkPos pos){manager.addTicket(TicketType.LIGHT,pos,redirect$zie000$redirectAddLightTicketDistance(ChunkStatus.LIGHT),pos);}
public void release(ChunkPos pos){ownerQueue.add(()->lambda$releaseLightTicket$30(pos));}
private void lambda$releaseLightTicket$30(ChunkPos pos){
manager.removeTicket(TicketType.LIGHT,pos,ChunkLevel.byStatus(ChunkStatus.LIGHT),pos);}
public void drain(){List<Runnable> copy=new ArrayList<>(ownerQueue);ownerQueue.clear();for(Runnable task:copy)task.run();}
public int pending(){return ownerQueue.size();} public int tickets(){return manager.tickets.size();}
}""",
}

PROBE = r'''
import com.axalotl.async.forge.client.C2meLightTicketLevels;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import java.nio.file.*;import java.lang.reflect.*;import java.util.*;
import net.minecraft.server.level.DistanceManager;import net.minecraft.world.level.ChunkPos;
public class LightProbe {
 static final String TARGET="net.minecraft.server.level.ChunkMap";
 static class Loader extends ClassLoader {final byte[] bytes;
 Loader(byte[] bytes){super(LightProbe.class.getClassLoader());this.bytes=bytes;}
 protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
 if(!name.equals(TARGET))return super.loadClass(name,resolve);
 synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);
 if(type==null)type=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(type);return type;}}}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static ClassNode read(byte[] bytes){ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);return n;}
 static byte[] write(ClassNode n){ClassWriter w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
 static void exercise(byte[] bytes,boolean paired)throws Exception{
 Class<?> type=new Loader(bytes).loadClass(TARGET);Object value=type.getConstructor().newInstance();
 Method add=type.getMethod("add",ChunkPos.class), release=type.getMethod("release",ChunkPos.class),drain=type.getMethod("drain");
 for(int i=0;i<529;i++){ChunkPos pos=new ChunkPos(i,1);add.invoke(value,pos);release.invoke(value,pos);}
 check((int)type.getMethod("tickets").invoke(value)==529,"original additions lost");
 check((int)type.getMethod("pending").invoke(value)==529,"original owner queue bypassed");
 drain.invoke(value);
 check((int)type.getMethod("tickets").invoke(value)==(paired?0:529),"LIGHT add/remove key mismatch");
 DistanceManager manager=(DistanceManager)type.getField("manager").get(value);
 check(manager.removals==529,"original removals lost or duplicated");
 if(paired){
 ChunkPos pos=new ChunkPos(999,2);add.invoke(value,pos);release.invoke(value,pos);
 RuntimeException original=new IllegalStateException("original ticket removal failure");manager.failure=original;
 try{drain.invoke(value);throw new AssertionError("original removal failure hidden");}
 catch(InvocationTargetException expected){check(expected.getCause()==original,"original failure identity changed");}
 check(manager.tickets.size()==1,"failed removal forcibly cleared original ticket");
 }
 }
 static ClassNode variant(byte[] original,int suffix,boolean mapped,boolean alreadyRedirected){
 ClassNode n=read(original);String releaseName="lambda$releaseLightTicket$"+suffix;
 String providerName="redirect$anotherPrefix000$redirectAddLightTicketDistance";
 for(MethodNode m:n.methods){
 if(m.name.equals("lambda$releaseLightTicket$30"))m.name=releaseName;
 if(m.name.endsWith("$redirectAddLightTicketDistance"))m.name=providerName;
 for(AbstractInsnNode i:m.instructions){
 if(i instanceof MethodInsnNode call){
 if(call.name.equals("lambda$releaseLightTicket$30"))call.name=releaseName;
 if(call.name.endsWith("$redirectAddLightTicketDistance"))call.name=providerName;
 if(mapped && call.owner.equals("net/minecraft/server/level/ChunkLevel") && call.name.equals("byStatus"))call.name="m_287141_";
 if(mapped && call.owner.equals("net/minecraft/server/level/DistanceManager") && call.name.equals("removeTicket"))call.name="m_140823_";
 }
 if(mapped && i instanceof FieldInsnNode f){
 if(f.owner.equals("net/minecraft/server/level/TicketType") && f.name.equals("LIGHT"))f.name="f_9446_";
 if(f.owner.equals("net/minecraft/world/level/chunk/ChunkStatus") && f.name.equals("LIGHT"))f.name="f_62323_";
 if(f.owner.equals("net/minecraft/world/level/chunk/ChunkStatus") && f.name.equals("FEATURES"))f.name="f_62315_";
 }
 }}
 if(alreadyRedirected){
 MethodNode release=n.methods.stream().filter(m->m.name.equals(releaseName)).findFirst().orElseThrow();
 for(AbstractInsnNode i:release.instructions.toArray()){
 if(i instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/level/ChunkLevel")){
 InsnList receiver=new InsnList();receiver.add(new VarInsnNode(Opcodes.ALOAD,0));receiver.add(new InsnNode(Opcodes.SWAP));
 release.instructions.insertBefore(call,receiver);release.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESPECIAL,n.name,providerName,call.desc,false));
 release.maxStack++;
 }}
 }
 return n;
 }
 static void expectDrift(ClassNode n){
 try{C2meLightTicketLevels.apply(n);throw new AssertionError("unsupported bytecode drift silently accepted");}
 catch(IllegalStateException expected){check(expected.getMessage().contains("source drift"),"unexpected error");}
 }
 public static void main(String[] args)throws Exception{
 byte[] original=Files.readAllBytes(Path.of(args[0]));exercise(original,false);
 int variants=0;
 for(int suffix:new int[]{7,30,57})for(boolean mapped:new boolean[]{false,true}){
 ClassNode n=variant(original,suffix,mapped,false);
 MethodNode release=n.methods.stream().filter(m->m.name.startsWith("lambda$releaseLightTicket$")).findFirst().orElseThrow();
 Map<MethodNode,List<AbstractInsnNode>> unchanged=new IdentityHashMap<>();
 for(MethodNode m:n.methods)if(m!=release)unchanged.put(m,Arrays.asList(m.instructions.toArray()));
 AbstractInsnNode remove=Arrays.stream(release.instructions.toArray()).filter(i->i instanceof MethodInsnNode c && (c.name.equals("removeTicket")||c.name.equals("m_140823_"))).findFirst().orElseThrow();
 int size=release.instructions.size(),handlers=release.tryCatchBlocks.size();
 check(C2meLightTicketLevels.apply(n),"leaking variant not repaired");
 check(release.instructions.size()==size+2 && release.tryCatchBlocks.size()==handlers,"original release body or handlers replaced");
 check(Arrays.asList(release.instructions.toArray()).contains(remove),"original removal invocation replaced");
 for(var entry:unchanged.entrySet())check(entry.getValue().equals(Arrays.asList(entry.getKey().instructions.toArray())),"unrelated method changed");
 check(!C2meLightTicketLevels.apply(n),"patch not idempotent");exercise(write(n),true);variants++;
 ClassNode paired=variant(original,suffix,mapped,true);
 check(!C2meLightTicketLevels.apply(paired),"already paired original changed");exercise(write(paired),true);
 }
 ClassNode disabled=read(original);disabled.methods.removeIf(m->m.name.endsWith("$redirectAddLightTicketDistance"));
 check(!C2meLightTicketLevels.apply(disabled),"disabled worldgen module changed");
 ClassNode duplicate=read(original);MethodNode provider=duplicate.methods.stream().filter(m->m.name.endsWith("$redirectAddLightTicketDistance")).findFirst().orElseThrow();
 duplicate.methods.add(provider);expectDrift(duplicate);
 ClassNode drift=read(original);drift.methods.stream().filter(m->m.name.startsWith("lambda$releaseLightTicket$")).findFirst().orElseThrow().name="unsupportedRelease";expectDrift(drift);
 System.out.println("LIGHT_TICKET_529_LEAK_NEGATIVE_CONTROL_AND_"+variants+"_PAIRED_VARIANTS_PASSED");
 }
}
'''

def main():
    p=argparse.ArgumentParser();p.add_argument("source",type=Path);p.add_argument("--report",type=Path,required=True);p.add_argument("--cache",type=Path,required=True);a=p.parse_args()
    a.cache.mkdir(parents=True,exist_ok=True)
    jars=[]
    for artifact in ("asm","asm-tree"):
        path=a.cache/f"{artifact}-9.5.jar"
        if not path.exists():
            with urllib.request.urlopen(f"https://repo.maven.apache.org/maven2/org/ow2/asm/{artifact}/9.5/{artifact}-9.5.jar",timeout=90) as response:path.write_bytes(response.read())
        jars.append(path.resolve())
    helper=a.source/"forge/src/main/java/com/axalotl/async/forge/client/C2meLightTicketLevels.java"
    with tempfile.TemporaryDirectory(prefix="hari-light-level-") as temp:
        root=Path(temp);files=dict(FILES);files["LightProbe.java"]=PROBE
        files["com/axalotl/async/forge/client/C2meLightTicketLevels.java"]=helper.read_text()
        for name,body in files.items():
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body)
        classes=root/"classes";cp=":".join(map(str,[classes,*jars]))
        subprocess.run(["java","--module","jdk.compiler/com.sun.tools.javac.Main","--release","17","-cp",cp,"-d",str(classes),*[str(path) for path in root.rglob("*.java")]],check=True)
        output=subprocess.check_output(["java","-ea","-cp",cp,"LightProbe",str(classes/"net/minecraft/server/level/ChunkMap.class")],text=True,stderr=subprocess.STDOUT,timeout=60)
        assert "LIGHT_TICKET_529_LEAK_NEGATIVE_CONTROL_AND_6_PAIRED_VARIANTS_PASSED" in output,output
    report={"passed":True,"production_helper_sha256":hashlib.sha256(helper.read_bytes()).hexdigest(),
        "original_negative_control_retained_LIGHT_tickets":529,"fixed_variants":6,
        "lambda_suffixes":[7,30,57],"development_and_native_names":True,
        "already_paired_idempotent":True,"worldgen_disabled_noop":True,
        "original_owner_queue_and_529_removal_operations":True,
        "original_exception_identity_preserved":True,"unsupported_drift_fails":True,
        "scope":"Real ASM and compiled Minecraft ticket-key causal fixtures matching exported original OptiFine+C2ME native instructions. Does not substitute for real native save/close/reopen."}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+"\n");print(output);print(json.dumps(report,indent=2))
if __name__=="__main__":main()
