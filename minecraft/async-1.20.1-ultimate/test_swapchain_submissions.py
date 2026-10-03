#!/usr/bin/env python3
"""Execute actual renderer submission methods against an acquire/present API trace."""
import argparse
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

NAMES = ['createSyncObjects', 'destroySyncObjects', 'submitFrame', 'flushCmds', 'flushForReadback', 'waitForSwapChain']
def method(source, name):
    marker = 'void ' + name + '('; start = source.index(marker)
    start = source.rfind('\n', 0, start) + 1
    opening = source.index('{', start); depth = 1; end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}'); end += 1
    return source[start:end]

SCAFFOLD = r'''
import java.util.*; import java.nio.*;
public class Renderer extends VkMock {
 static Object device = new Object(); static int currentFrame, imageIndex;
 int framesNum=2, imagesNum=3; boolean recordingCmds=true, swapChainAcquirePending;
 static boolean swapChainUpdate; ArrayList<Long> imageAvailableSemaphores, renderFinishedSemaphores, inFlightFences;
 VkCommandBuffer currentCmdBuffer=new VkCommandBuffer(); RenderPass boundRenderPass=new RenderPass(); Framebuffer boundFramebuffer=new Framebuffer();
 void endRenderPass(VkCommandBuffer b){boundRenderPass=null;}
 void beginRenderPass(MemoryStack s){recordingCmds=true;boundRenderPass=new RenderPass();boundFramebuffer=new Framebuffer();}
 void invalidateRenderState(){}
 static void resetDynamicState(VkCommandBuffer buffer){}
METHODS
 static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
 void acquire(int frame,int image){currentFrame=frame;imageIndex=image;swapChainAcquirePending=true;gpuPending=true;waits=0;recordingCmds=true;boundRenderPass=new RenderPass();boundFramebuffer=new Framebuffer();live=this;}
 public static void main(String[] args){
  Renderer r=new Renderer();live=r;r.createSyncObjects();
  if(args.length>0){r.acquire(0,1);try{r.flushCmds();throw new AssertionError("Old flush unexpectedly waited");}catch(IllegalStateException expected){require(expected.getMessage().contains("WRITE_AFTER_PRESENT"),expected.toString());}System.out.println("OLD_FLUSH_HAZARD_REPRODUCED");return;}
  require(r.imageAvailableSemaphores.size()==2,"frame acquire semaphore count");
  require(r.renderFinishedSemaphores.size()==3,"present semaphore count follows actual images");
  int[] images={2,0,2,1,0,1};
  for(int i=0;i<2000;i++){
   r.acquire(i%2,images[i%images.length]);
   switch(i%4){case 1:r.flushCmds();r.flushForReadback();r.flushCmds();break;case 2:r.flushForReadback();r.flushCmds();r.flushForReadback();break;case 3:r.waitForSwapChain();r.waitForSwapChain();r.flushCmds();break;}
   r.submitFrame();require(waits==1&&!gpuPending&&!r.swapChainAcquirePending,"exactly one consumed acquire wait for each frame");
  }
  r.acquire(0,2);failure=true;try{r.flushForReadback();throw new AssertionError("Submission failure hidden");}catch(RuntimeException expected){require(r.swapChainAcquirePending&&gpuPending,"failed submit must retain pending acquire");}
  r.destroySyncObjects();require(destroyedSemaphores==5,"destroy each frame/image semaphore once");
  System.out.println("ACTUAL_SUBMISSIONS_PASSED_2000_FRAMES");
 }
}
class VkMock {
 static final int VK_SUCCESS=0,VK_STRUCTURE_TYPE_SUBMIT_INFO=1,VK_STRUCTURE_TYPE_PRESENT_INFO_KHR=2,VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO=3,VK_STRUCTURE_TYPE_FENCE_CREATE_INFO=4,VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO=5,VK_FENCE_CREATE_SIGNALED_BIT=1,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT=1,VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT=1024,VK_PIPELINE_STAGE_ALL_COMMANDS_BIT=65536,VK_ERROR_OUT_OF_DATE_KHR=-1,VK_SUBOPTIMAL_KHR=1;
 static Renderer live;static boolean gpuPending,failure;static int waits,destroyedSemaphores;static long next=100,signal;
 static MemoryStack stackPush(){return new MemoryStack();} static SwapChain getSwapChain(){return new SwapChain();}
 static int vkCreateSemaphore(Object d,VkSemaphoreCreateInfo i,Object a,LongBuffer p){p.put(0,next++);return 0;}
 static int vkCreateFence(Object d,VkFenceCreateInfo i,Object a,LongBuffer p){p.put(0,next++);return 0;}
 static void vkDestroySemaphore(Object d,long s,Object a){destroyedSemaphores++;}static void vkDestroyFence(Object d,long f,Object a){}
 static int vkResetFences(Object d,long f){return 0;}static int vkWaitForFences(Object d,long f,boolean a,long t){return 0;}
 static int vkResetCommandBuffer(VkCommandBuffer b,int f){return 0;}static int vkBeginCommandBuffer(VkCommandBuffer b,VkCommandBufferBeginInfo i){return 0;}static int vkEndCommandBuffer(VkCommandBuffer b){return 0;}
 static int vkQueueSubmit(Object q,VkSubmitInfo s,long f){
  if(failure){failure=false;return -2;}
  if(s.waitCount==1){
   if(!gpuPending)throw new IllegalStateException("DOUBLE_WAIT");
   if(s.waits.get(0)!=live.imageAvailableSemaphores.get(Renderer.currentFrame))throw new AssertionError("wrong acquire semaphore");
   if(s.stages.get(0)!=VK_PIPELINE_STAGE_ALL_COMMANDS_BIT)throw new AssertionError("layout/transfer stage not covered");
   waits++;gpuPending=false;
  }
  if(s.commands!=null&&gpuPending)throw new IllegalStateException("WRITE_AFTER_PRESENT: actual early flush submitted without acquire wait");
  if(s.signals!=null)signal=s.signals.get(0);return 0;
 }
 static int vkQueuePresentKHR(Object q,VkPresentInfoKHR i){
  if(i.waits.get(0)!=signal||signal!=live.renderFinishedSemaphores.get(Renderer.imageIndex))throw new AssertionError("present wait does not belong to acquired image");return 0;
 }
}
class MemoryStack implements AutoCloseable {
 static MemoryStack stackPush(){return new MemoryStack();}LongBuffer mallocLong(int n){return LongBuffer.allocate(n);}LongBuffer longs(long... a){return LongBuffer.wrap(a);}IntBuffer ints(int... a){return IntBuffer.wrap(a);}VkCommandBuffer[] pointers(VkCommandBuffer b){return new VkCommandBuffer[]{b};}public void close(){}
}
class VkSubmitInfo {
 int waitCount;LongBuffer waits,signals;IntBuffer stages;VkCommandBuffer[] commands;
 static VkSubmitInfo calloc(MemoryStack s){return new VkSubmitInfo();}VkSubmitInfo sType(int t){return this;}VkSubmitInfo sType$Default(){return this;}
 VkSubmitInfo waitSemaphoreCount(int n){waitCount=n;return this;}VkSubmitInfo pWaitSemaphores(LongBuffer p){waits=p;waitCount=p.remaining();return this;}VkSubmitInfo pWaitDstStageMask(IntBuffer p){stages=p;return this;}VkSubmitInfo pSignalSemaphores(LongBuffer p){signals=p;return this;}VkSubmitInfo pCommandBuffers(VkCommandBuffer[] p){commands=p;return this;}
}
class VkPresentInfoKHR {LongBuffer waits;static VkPresentInfoKHR calloc(MemoryStack s){return new VkPresentInfoKHR();}VkPresentInfoKHR sType(int t){return this;}VkPresentInfoKHR pWaitSemaphores(LongBuffer p){waits=p;return this;}VkPresentInfoKHR swapchainCount(int n){return this;}VkPresentInfoKHR pSwapchains(LongBuffer p){return this;}VkPresentInfoKHR pImageIndices(IntBuffer p){return this;}}
class VkSemaphoreCreateInfo {static VkSemaphoreCreateInfo calloc(MemoryStack s){return new VkSemaphoreCreateInfo();}void sType(int t){}}
class VkFenceCreateInfo {static VkFenceCreateInfo calloc(MemoryStack s){return new VkFenceCreateInfo();}void sType(int t){}void flags(int t){}}
class VkCommandBufferBeginInfo {static VkCommandBufferBeginInfo calloc(MemoryStack s){return new VkCommandBufferBeginInfo();}void sType(int t){}void flags(int t){}}
class VkCommandBuffer{} class RenderPass{} class Framebuffer{void beginRenderPass(VkCommandBuffer c,RenderPass r,MemoryStack s){}}
class SwapChain{long getId(){return 1;}}class Vulkan{static SwapChain getSwapChain(){return new SwapChain();}}
class VkResult{static String decode(int n){return Integer.toString(n);}}class VUtil{static final long UINT64_MAX=-1;}
class DeviceManager{static Queue getGraphicsQueue(){return new Queue();}static Queue getPresentQueue(){return new Queue();}}class Queue{Object queue(){return this;}}
class Synchronization{static final Synchronization INSTANCE=new Synchronization();void waitFences(){}}
'''

def execute(source, old=False):
    methods = [method(source, name) for name in NAMES]
    if 'void prepareSwapChainSubmit(' in source:
        methods.append(method(source, 'prepareSwapChainSubmit'))
    with tempfile.TemporaryDirectory() as td:
        p=Path(td);(p/'Renderer.java').write_text(SCAFFOLD.replace('METHODS','\n'.join(methods)))
        subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-d',str(p),str(p/'Renderer.java')],check=True,capture_output=True,text=True)
        result=subprocess.run(['java','-cp',str(p),'Renderer']+(['old'] if old else []),check=True,capture_output=True,text=True)
        return result.stdout.strip()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('--baseline',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    source=a.source.read_text();old=a.baseline.read_text()
    assert 'imageIndex = pImageIndex.get(0);\n            swapChainAcquirePending = true;' in source
    assert execute(source)=='ACTUAL_SUBMISSIONS_PASSED_2000_FRAMES'
    assert execute(old,True)=='OLD_FLUSH_HAZARD_REPRODUCED'
    a.report.parent.mkdir(parents=True,exist_ok=True)
    a.report.write_text(json.dumps({'passed':True,'actual_methods':NAMES+['prepareSwapChainSubmit'],'frames':2000,'early_flush_and_readback_wait_once':True,'submission_failure_retains_pending_wait':True,'per_image_present_semaphores':True,'old_flush_hazard_reproduced':True,'production_source_sha256':hashlib.sha256(source.encode()).hexdigest(),'scope':'Actual production Java submission methods executed against explicit Vulkan API trace doubles. Native packaged Minecraft synchronization validation remains required.'},indent=2)+'\n')
    print('Actual renderer submission regression passed; original unawaited flush reproduced')
