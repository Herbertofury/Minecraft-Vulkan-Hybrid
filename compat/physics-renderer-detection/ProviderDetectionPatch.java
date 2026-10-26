import java.nio.file.*;import java.util.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public class ProviderDetectionPatch{
 public static void main(String[]a)throws Exception{
 byte[] input=Files.readAllBytes(Path.of(a[0]));ClassNode c=new ClassNode();new ClassReader(input).accept(c,0);int changed=0;MethodNode selected=null;
 for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&f.owner.equals("net/diebuddies/physics/StarterClient")&&f.name.equals("iris")&&f.desc.equals("Z")){
  AbstractInsnNode next=n.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();if(next instanceof JumpInsnNode j&&j.getOpcode()==Opcodes.IFNE){m.instructions.set(n,new InsnNode(Opcodes.ICONST_0));changed++;selected=m;}
 }
 if(changed!=1)throw new AssertionError("Expected exactly one wrong iris-implies-sodium branch");
 ClassWriter writer=new ClassWriter(0);c.accept(writer);Files.write(Path.of(a[1]),writer.toByteArray());
 // Extract the actual patched provider decision, without executing the upstream initializer.
 AbstractInsnNode first=null,last=null;for(AbstractInsnNode n:selected.instructions.toArray()){
  if(first==null&&n instanceof LdcInsnNode l&&l.cst.equals("sodium"))first=n;
  if(first!=null&&n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC&&f.owner.equals(c.name)&&f.name.equals("sodium")){last=n;break;}
 }
 if(first==null||last==null)throw new AssertionError("Provider selection not found");
 ClassWriter control=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);control.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,c.name,null,"java/lang/Object",null);
 control.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"iris","Z",null,null).visitEnd();control.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"sodium","Z",null,null).visitEnd();
 MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"test","()V",null,null);Map<LabelNode,LabelNode> labels=new HashMap<>();for(AbstractInsnNode n=first;n!=null;n=n.getNext()){if(n instanceof LabelNode l)labels.put(l,new LabelNode());if(n==last)break;}
 for(AbstractInsnNode n=first;n!=null;n=n.getNext()){if(!(n instanceof FrameNode)&&!(n instanceof LineNumberNode))method.instructions.add(n.clone(labels));if(n==last)break;}
 method.instructions.add(new InsnNode(Opcodes.RETURN));method.accept(control);control.visitEnd();Path q=Path.of(a[2]).resolve(c.name+".class");Files.createDirectories(q.getParent());Files.write(q,control.toByteArray());
 }
}