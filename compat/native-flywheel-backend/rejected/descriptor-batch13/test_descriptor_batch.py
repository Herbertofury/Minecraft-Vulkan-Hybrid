"""Check actual descriptor builders/update guard with an independent payload oracle.

Checked Vulkan structure doubles exercise production Java methods. Actual native
driver/descriptors and visual parity require the separate Minecraft fixture.
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
 source=Path(__file__).resolve().parent/'NativeWorldRenderer.java';text=source.read_text()
 actual='\n'.join(block(text,s) for s in ['private void writeBuffer(','private void writeImage(','private void updateDescriptors('])
 fixture='''import java.util.*;import java.util.concurrent.atomic.AtomicLong;
 public class DescriptorRegression {
 static int checks,calls;static long device=9;
 static final int VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER=6,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER=7,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER=1,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL=5;
 static class NativeEngine{static final AtomicLong DESCRIPTOR_UPDATE_CALLS=new AtomicLong(),DESCRIPTORS_WRITTEN=new AtomicLong();}
 static class MemoryStack{}static class Buffer{long buffer;Buffer(long handle){buffer=handle;}}
 record Slice(Buffer buffer,int offset,int bytes){}record VulkanImage(long view){long getImageView(){return view;}}
 static class VkDescriptorBufferInfo{static Buffer calloc(int n,MemoryStack s){if(n!=1)throw new AssertionError();return new Buffer();}static class Buffer{long buffer,offset,range;Buffer buffer(long v){buffer=v;return this;}Buffer offset(long v){offset=v;return this;}Buffer range(long v){range=v;return this;}}}
 static class VkDescriptorImageInfo{static Buffer calloc(int n,MemoryStack s){if(n!=1)throw new AssertionError();return new Buffer();}static class Buffer{long imageView,sampler;int imageLayout;Buffer imageView(long v){imageView=v;return this;}Buffer sampler(long v){sampler=v;return this;}Buffer imageLayout(int v){imageLayout=v;return this;}}}
 static class VkWriteDescriptorSet {
  long set;int binding,count,type,structure;VkDescriptorBufferInfo.Buffer buffer;VkDescriptorImageInfo.Buffer image;
  VkWriteDescriptorSet sType$Default(){structure=35;return this;}VkWriteDescriptorSet dstSet(long v){set=v;return this;}VkWriteDescriptorSet dstBinding(int v){binding=v;return this;}VkWriteDescriptorSet descriptorCount(int v){count=v;return this;}VkWriteDescriptorSet descriptorType(int v){type=v;return this;}VkWriteDescriptorSet pBufferInfo(VkDescriptorBufferInfo.Buffer v){buffer=v;return this;}VkWriteDescriptorSet pImageInfo(VkDescriptorImageInfo.Buffer v){image=v;return this;}
  static Buffer calloc(int n,MemoryStack s){return new Buffer(n);}static class Buffer{int position;final VkWriteDescriptorSet[] data;Buffer(int n){data=new VkWriteDescriptorSet[n];Arrays.setAll(data,i->new VkWriteDescriptorSet());}VkWriteDescriptorSet get(int i){return data[i];}int position(){return position;}Buffer position(int v){position=v;return this;}int remaining(){return data.length-position;}}
 }
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final List<List<Long>> submitted=new ArrayList<>();
 static void vkUpdateDescriptorSets(long d,VkWriteDescriptorSet.Buffer writes,Object copies){check(d==9&&copies==null,"Wrong native update target");calls++;submitted.clear();for(int i=writes.position;i<writes.data.length;i++){var w=writes.data[i];var values=new ArrayList<Long>(List.of((long)w.structure,w.set,(long)w.binding,(long)w.count,(long)w.type));if(w.buffer!=null)values.addAll(List.of(w.buffer.buffer,w.buffer.offset,w.buffer.range));else if(w.image!=null)values.addAll(List.of(w.image.imageView,w.image.sampler,(long)w.image.imageLayout));else throw new AssertionError("Descriptor without original resource payload");submitted.add(values);}}
 '''
 suffix='''
 void run(){MemoryStack stack=new MemoryStack();var compute=VkWriteDescriptorSet.calloc(14,stack);
  for(int i=0;i<14;i++)writeBuffer(stack,compute,i,i<6?11:22,i<6?i:i-6,new Slice(new Buffer(1001+i),64+i*4,128+i),i<6?6:7);
  check(calls==0,"Builder issued separate native calls");updateDescriptors(compute);
  check(calls==1&&submitted.size()==14,"Compute must issue one complete batch");
  for(int i=0;i<14;i++)check(submitted.get(i).equals(List.of(35L,i<6?11L:22L,(long)(i<6?i:i-6),1L,i<6?6L:7L,1001L+i,64L+i*4,128L+i)),"Original compute descriptor slot/resource/range mismatch "+i);
  var graphics=VkWriteDescriptorSet.calloc(14,stack);
  for(int i=0;i<10;i++)writeBuffer(stack,graphics,i,i<6?11:22,i<6?i:i-6,new Slice(new Buffer(2001+i),256+i*4,64+i),i<6?6:7);
  for(int i=0;i<4;i++)writeImage(stack,graphics,10+i,33,i,new VulkanImage(3001+i),4001+i);
  check(calls==1,"Image builder issued separate native calls");updateDescriptors(graphics);check(calls==2&&submitted.size()==14,"Graphics must issue one complete batch");
  for(int i=0;i<10;i++)check(submitted.get(i).equals(List.of(35L,i<6?11L:22L,(long)(i<6?i:i-6),1L,i<6?6L:7L,2001L+i,256L+i*4,64L+i)),"Original graphics buffer descriptor mismatch "+i);
  for(int i=0;i<4;i++)check(submitted.get(10+i).equals(List.of(35L,33L,(long)i,1L,1L,3001L+i,4001L+i,5L)),"Original image view/sampler/layout mismatch "+i);
  graphics.position(1);try{updateDescriptors(graphics);throw new AssertionError("Partial descriptor batch accepted");}catch(IllegalStateException expected){}check(calls==2,"Partial batch reached native call");
  check(NativeEngine.DESCRIPTOR_UPDATE_CALLS.get()==2&&NativeEngine.DESCRIPTORS_WRITTEN.get()==28,"Real descriptor counters disagree");
  System.out.println("DESCRIPTOR_PAYLOAD_CONTROLS="+checks+" NATIVE_CALLS="+calls);
 }public static void main(String[] args){new DescriptorRegression().run();}}
 '''
 variants=[('production',actual,True),('shifts_binding',actual.replace('.dstBinding(binding)','.dstBinding(binding+1)'),False),('loses_range',actual.replace('.range(slice.bytes)','.range(4)'),False),('loses_sampler',actual.replace('.sampler(sampler)','.sampler(0)'),False),('accepts_partial',actual.replace('writes.position()!=0||writes.remaining()!=14','false'),False)]
 results=[]
 for name,code,expected in variants:
  with tempfile.TemporaryDirectory(prefix='mvh-native-descriptors-') as folder:
   root=Path(folder);java=root/'DescriptorRegression.java';java.write_text(fixture+code+suffix)
   r=subprocess.run(['javac','--release','17','-d',str(root),str(java)],capture_output=True,text=True,timeout=30);assert r.returncode==0,r.stderr
   r=subprocess.run(['java','-ea','-cp',str(root),'DescriptorRegression'],capture_output=True,text=True,timeout=20);passed=r.returncode==0;assert passed==expected,(name,r.stdout,r.stderr)
   if not expected:assert 'AssertionError' in r.stderr
   results.append({'variant':name,'behavior_passed':passed,'expected':expected,'stdout':r.stdout.strip()})
 report={'passed':True,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'variants':results,'scope':'Actual descriptor builder and update guard with checked Vulkan structure doubles and an independent expected payload. Compute6UBO+8SSBO and graphics6UBO+4SSBO+4image retain all slots/resource extents/samplers. Four behavior mutations rejected. Not actual GPU or FPS evidence.'}
 if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,indent=2)+'\n')
 print('DESCRIPTOR_BATCH_PAYLOAD_PASS REJECTED_MUTATIONS=4')
if __name__=='__main__':main()
