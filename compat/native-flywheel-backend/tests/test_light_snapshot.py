"""Compile actual snapshot/lifetime methods against a checked memory allocator.

This checks immutable data, revision invalidation, fence-boundary retirement and
allocation failure cleanup. It is CPU evidence, not GPU, lighting parity or FPS.
"""
from pathlib import Path
import argparse,hashlib,json,subprocess,tempfile

def block(text,start):
 left=text.index(start);brace=text.index('{',left);level=1;end=brace+1
 while level:
  if text[end]=='{':level+=1
  elif text[end]=='}':level-=1
  end+=1
 return text[left:end]

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);a=ap.parse_args()
 source=Path(__file__).resolve().parents[1]/'src/main/java/mvhflywheelbackend/NativeWorldRenderer.java'
 text=source.read_text();frame_text=block(text,'private final class Frame {')
 methods='\n'.join(block(frame_text,s) for s in ['void lightSnapshot(','void begin(','void destroy('])
 snapshot=block(text,'private final class LightSnapshot {')
 prefix='''import java.util.*;import java.util.concurrent.atomic.AtomicLong;import java.nio.*;
 public class SnapshotRegression {
 static int checks,allocations,destroys,failAt=-1;static long device=1;
 static final int VK_BUFFER_USAGE_STORAGE_BUFFER_BIT=1;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static void check(int result,String message){if(result!=0)throw new AssertionError(message);}
 static int vkResetDescriptorPool(long d,long p,int f){return 0;}static void vkDestroyDescriptorPool(long d,long p,Object x){}
 static class NativeEngine {static final AtomicLong LIGHT_CACHE_HITS=new AtomicLong(),LIGHT_UPLOADS=new AtomicLong(),LIGHT_UPLOAD_BYTES=new AtomicLong(),GPU_LAST_VISIBLE=new AtomicLong(),GPU_LAST_SUBMITTED=new AtomicLong();}
 static class MemoryUtil {
  static final Map<Long,byte[]> memory=new HashMap<>();static long next=1;
  static long register(int size){long address=(next++)<<32;memory.put(address,new byte[size]);return address;}
  static byte[] at(long p){byte[] b=memory.get(p&0xffffffff00000000L);if(b==null)throw new AssertionError("Use after free");return b;}
  static int offset(long p){return (int)p;}static int memGetInt(long p){return ByteBuffer.wrap(at(p)).order(ByteOrder.LITTLE_ENDIAN).getInt(offset(p));}
  static void memPutInt(long p,int v){ByteBuffer.wrap(at(p)).order(ByteOrder.LITTLE_ENDIAN).putInt(offset(p),v);}
  static void memSet(long p,int v,int n){Arrays.fill(at(p),offset(p),Math.addExact(offset(p),n),(byte)v);}
  static void memCopy(long p,long q,int n){System.arraycopy(at(p),offset(p),at(q),offset(q),n);}
 }
 static class Buffer {final int bytes;final long pointer;boolean freed;Buffer(int bytes,int usage){allocations++;if(allocations==failAt)throw new IllegalStateException("Allocation failure");this.bytes=bytes;pointer=MemoryUtil.register(bytes);}void destroy(){if(freed)throw new AssertionError("Double free");freed=true;MemoryUtil.memory.remove(pointer);destroys++;}}
 record Slice(Buffer buffer,int offset,int bytes){long pointer(){if(buffer.freed)throw new AssertionError("Retired slice used");return buffer.pointer+offset;}}
 static class Arena {final Buffer data;Arena(int bytes,int value){data=new Buffer(Math.max(4,bytes),1);MemoryUtil.memSet(data.pointer,value,bytes);size=bytes;}final int size;long byteCapacity(){return size;}long indexToPointer(int i){return data.pointer+i;}}
 static class Lut {final int[] data;Lut(int...data){this.data=data;}int size(){return data.length;}int getInt(int i){return data[i];}}
 static class LightStorage {long revision;Arena arena;Lut lut;int lutBuilds;LightStorage(long revision,int bytes,int fill,int...indices){this.revision=revision;arena=new Arena(bytes,fill);lut=new Lut(indices);}long nativeLightRevision(){return revision;}Lut createLut(){lutBuilds++;return lut;}}
 static class Prepared {Slice models;int submitted;}
 Slice lightLut,lightSections;
 private final class Frame {
  long epoch=-1;final ArrayList<Buffer> chunks=new ArrayList<>();final ArrayList<Long> pools=new ArrayList<>();int chunk,offset,pool,sets;
  final ArrayList<Prepared> feedback=new ArrayList<>();final ArrayList<LightSnapshot> lightVersions=new ArrayList<>();LightSnapshot latestLight;long lightUseEpoch=-1;
 '''
 suffix='''
 static byte[] bytes(Slice s){return Arrays.copyOfRange(MemoryUtil.at(s.pointer()),MemoryUtil.offset(s.pointer()),MemoryUtil.offset(s.pointer())+s.bytes);}
 void run(){
  LightStorage light=new LightStorage(1,16,17,7,8);Frame f=new Frame();f.begin(1);f.lightSnapshot(light);
  Slice oldSections=lightSections,oldLut=lightLut;byte[] oldBytes=bytes(oldSections);int firstAllocs=allocations;
  check(MemoryUtil.memGetInt(oldLut.pointer())==7&&MemoryUtil.memGetInt(oldLut.pointer()+4)==8,"Original LUT not copied");
  check(oldBytes.length==16&&oldBytes[15]==17,"Original light section arena not copied");
  f.lightSnapshot(light);check(allocations==firstAllocs&&light.lutBuilds==1&&lightSections.buffer==oldSections.buffer,"Unchanged revision should reuse exact snapshot");
  MemoryUtil.memSet(light.arena.data.pointer,34,16);light.lut=new Lut(9,10);light.revision++;
  f.lightSnapshot(light);check(lightSections.buffer!=oldSections.buffer,"Changed same-epoch light overwrote an in-flight snapshot");
  check(Arrays.equals(bytes(oldSections),oldBytes)&&MemoryUtil.memGetInt(oldLut.pointer())==7,"Original in-flight data mutated");
  check(bytes(lightSections)[15]==34&&MemoryUtil.memGetInt(lightLut.pointer())==9,"Changed light/LUT not uploaded");
  check(!oldSections.buffer.freed&&f.lightVersions.size()==2,"Old light freed before frame fence");
  f.begin(1);check(!oldSections.buffer.freed&&f.lightVersions.size()==2,"Same epoch retired GPU-referenced light");
  Slice changed=lightSections;f.begin(2);check(oldSections.buffer.freed&&oldLut.buffer.freed&&!changed.buffer.freed&&f.lightVersions.size()==1,"Fence completion did not retire only old versions");
  light.revision++;MemoryUtil.memSet(light.arena.data.pointer,51,16);int previousAllocs=allocations;f.lightSnapshot(light);
  check(lightSections.buffer==changed.buffer&&allocations==previousAllocs&&bytes(lightSections)[15]==51,"Fence-completed capacity should reuse allocation with fresh data");
  Frame another=new Frame();another.begin(2);another.lightSnapshot(light);check(lightSections.buffer!=changed.buffer,"Different frame owners shared writable storage");
  another.destroy();Slice retained=f.latestLight.sections;
  f.begin(3);light.arena=new Arena(64,68);light.lut=new Lut(11,12,13,14);light.revision++;f.lightSnapshot(light);
  check(lightSections.buffer!=retained.buffer&&bytes(lightSections).length==64&&bytes(lightSections)[63]==68,"Capacity growth did not allocate full arena");
  check(!retained.buffer.freed,"Growth retired a version before its frame fence");f.begin(4);check(retained.buffer.freed,"Growth old allocation leaked across fence");
  f.destroy();check(f.lightVersions.isEmpty()&&f.latestLight==null,"Owner destruction leaked light versions");
  Frame empty=new Frame();empty.begin(1);empty.lightSnapshot(new LightStorage(0,0,0));check(lightLut.bytes==4&&lightSections.bytes==4&&MemoryUtil.memGetInt(lightLut.pointer())==0&&MemoryUtil.memGetInt(lightSections.pointer())==0,"Empty original light must be deterministic nonzero buffers");empty.destroy();
  Frame failure=new Frame();failure.begin(1);LightStorage failLight=new LightStorage(1,16,1,2);int before=destroys;failAt=allocations+2;
  try{failure.lightSnapshot(failLight);throw new AssertionError("Injected allocation failure ignored");}catch(IllegalStateException expected){}
  check(destroys==before+1&&failure.latestLight==null&&failure.lightVersions.isEmpty(),"Second allocation failure leaked first buffer");failAt=-1;failure.destroy();
  check(NativeEngine.LIGHT_CACHE_HITS.get()==1&&NativeEngine.LIGHT_UPLOADS.get()==6,"Upload/cache counters lost actual work");
  System.out.println("LIGHT_SNAPSHOT_CONTROLS="+checks);
 }
 public static void main(String[] args){new SnapshotRegression().run();}
 }
 '''
 variants=[('production',methods,snapshot,True),
  ('overwrites_in_flight',methods.replace('lightUseEpoch==epoch||',''),snapshot,False),
  ('ignores_light_revision',methods.replace('latestLight.revision==revision','true'),snapshot,False),
  ('retires_during_same_epoch',methods.replace('if(epoch==next)return;',''),snapshot,False),
  ('leaks_partial_allocation',methods,snapshot.replace('lookup.destroy();',''),False)]
 results=[]
 for name,code,snap,expected in variants:
  with tempfile.TemporaryDirectory(prefix='mvh-light-snapshot-') as folder:
   root=Path(folder);java=root/'SnapshotRegression.java';java.write_text(prefix+code+'}\n'+snap+suffix)
   result=subprocess.run(['javac','--release','17','-d',str(root),str(java)],capture_output=True,text=True,timeout=30)
   assert result.returncode==0,result.stderr
   r=subprocess.run(['java','-ea','-cp',str(root),'SnapshotRegression'],capture_output=True,text=True,timeout=20)
   passed=r.returncode==0;assert passed==expected,(name,r.stdout,r.stderr)
   if not expected:assert 'AssertionError' in r.stderr
   results.append({'variant':name,'behavior_passed':passed,'expected':expected,'stdout':r.stdout.strip()})
 report={'passed':True,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'variants':results,'scope':'Actual production methods with checked CPU memory allocator; immutable revisions, same-epoch protection, frame-fence retirement, owner isolation, capacity growth, empty input and partial allocation cleanup. Four behavior mutations rejected. No GPU, visual-parity or FPS claim.'}
 if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
 print('LIGHT_SNAPSHOT_LIFETIME_PASS REJECTED_MUTATIONS=4')
if __name__=='__main__':main()
