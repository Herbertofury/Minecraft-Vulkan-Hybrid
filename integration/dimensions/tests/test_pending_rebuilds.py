"""Compile the real scheduler body and queue; exercise failed, deferred and stale build ownership."""
from pathlib import Path
import argparse,json,subprocess,tempfile
I=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--report',type=Path);args=parser.parse_args()
text=(I/'overlay/forge/src/main/java/net/vulkanmod/render/chunk/graph/SectionGraph.java').read_text()
def method(name):
    start=text.index('    '+name);opening=text.index('{',start);depth=1;end=opening+1
    while depth:
        if text[end]=='{':depth+=1
        elif text[end]=='}':depth-=1
        end+=1
    return text[start:end]
body=method('private void scheduleRebuilds()')
continuation=method('public void continuePendingRebuilds()')
queue=(I/'overlay/forge/src/main/java/net/vulkanmod/render/chunk/util/ResettableQueue.java').read_text()
world=(I/'overlay/forge/src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java').read_text()
assert 'this.sectionGraph.update(camera, frustum, spectator);' in world
assert '} else {\n                this.sectionGraph.continuePendingRebuilds();' in world
prefix='''import java.util.*;import net.vulkanmod.render.chunk.util.ResettableQueue;
public class PendingProbe {
 final ResettableQueue<RenderSection> rebuildQueue=new ResettableQueue<>();
 final Object taskDispatcher=new Object(),renderRegionCache=new Object();
 void visible(RenderSection... sections){rebuildQueue.clear();for(RenderSection s:sections){rebuildQueue.ensureCapacity(1);rebuildQueue.add(s);}}
'''
suffix='''
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  PendingProbe p=new PendingProbe();RenderSection first=new RenderSection(),second=new RenderSection(),unloaded=new RenderSection();unloaded.ready=false;
  AdaptiveChunkUploadBudget.budget=1;p.visible(unloaded,first,second);p.continuePendingRebuilds();
  check(first.submits==1&&second.submits==0&&unloaded.submits==0,"Budget/order changed");check(p.rebuildQueue.size()==2,"Lost unscheduled build");
  p.continuePendingRebuilds();check(second.submits==1&&p.rebuildQueue.size()==1,"Deferred ready section lost");
  for(int i=0;i<100;i++)p.continuePendingRebuilds();check(unloaded.submits==0&&p.rebuildQueue.size()==1,"Unloaded build not retained");
  unloaded.ready=true;p.continuePendingRebuilds();check(unloaded.submits==1&&p.rebuildQueue.size()==0,"Readiness transition lost");
  p.continuePendingRebuilds();check(first.submits==1&&second.submits==1&&unloaded.submits==1,"Duplicate submission");
  // A pending section independently becomes clean; it must not cancel/resubmit its existing task.
  RenderSection cleaned=new RenderSection();cleaned.ready=false;p.visible(cleaned);p.continuePendingRebuilds();cleaned.dirty=false;cleaned.ready=true;p.continuePendingRebuilds();check(cleaned.submits==0&&p.rebuildQueue.size()==0,"Clean snapshot rebuilt");
  // A new camera/frustum traversal replaces pending identities; the old range is not scheduled.
  RenderSection old=new RenderSection(),current=new RenderSection();old.ready=false;p.visible(old);p.continuePendingRebuilds();p.visible(current);old.ready=true;p.continuePendingRebuilds();check(old.submits==0&&current.submits==1,"Old visibility range retained");
  // Budget zero and repeated failures keep all candidates. Original failure identity propagates.
  RenderSection failed=new RenderSection();AdaptiveChunkUploadBudget.budget=0;p.visible(failed);p.continuePendingRebuilds();check(failed.attempts==0&&p.rebuildQueue.size()==1,"Zero budget violated");
  AdaptiveChunkUploadBudget.budget=1;RuntimeException fault=new RuntimeException("original build fault");failed.fault=fault;try{p.continuePendingRebuilds();throw new AssertionError("Fault swallowed");}catch(RuntimeException error){check(error==fault,"Fault identity changed");}
  failed.fault=null;p.continuePendingRebuilds();check(failed.submits==1&&p.rebuildQueue.size()==0,"Recovery lost pending identity");
  check(WorldRenderer.invalidations==0,"Build retry incorrectly invalidated visibility");
  ResettableQueue<Object> q=new ResettableQueue<>(1);Object a=new Object(),b=new Object();q.add(a);q.ensureCapacity(1);q.add(b);q.poll();q.poll();q.set(0,b);q.truncate(1);check(q.size()==1&&!q.hasNext()&&q.get(0)==b,"Queue compaction contract");q.rewind();check(q.poll()==b,"Retained item identity");
  try{q.truncate(2);throw new AssertionError("Out of range truncate accepted");}catch(IndexOutOfBoundsException expected){}
  System.out.println("ACTUAL_PENDING_REBUILD_BUDGET_READINESS_VISIBILITY_FAILURE_PASS");
 }
}
class RenderSection{boolean dirty=true,ready=true;int attempts,submits;RuntimeException fault;boolean isDirty(){return dirty;}boolean rebuildChunkAsync(Object dispatcher,Object cache){attempts++;if(fault!=null)throw fault;if(!ready)return false;submits++;return true;}void setNotDirty(){dirty=false;}}
class AdaptiveChunkUploadBudget{static int budget=1;static int chooseRebuildScheduleBudget(int count){return Math.min(budget,count);}}
class WorldRenderer{static int invalidations;static final WorldRenderer INSTANCE=new WorldRenderer();static WorldRenderer getInstance(){return INSTANCE;}void scheduleGraphUpdate(){invalidations++;}}
'''
variants=[('production',body,True),('deferred_builds_lost',body.replace('this.rebuildQueue.truncate(retained);','this.rebuildQueue.clear();'),False),('clean_sections_resubmitted',body.replace('if (!section.isDirty()) continue;',''),False)]
results=[]
for label,actual,expected in variants:
    with tempfile.TemporaryDirectory(prefix='mvh-pending-builds-') as folder:
        root=Path(folder);sources=[]
        for name,value in [('PendingProbe.java',prefix+actual+'\n'+continuation+suffix),('net/vulkanmod/render/chunk/util/ResettableQueue.java',queue),('org/jetbrains/annotations/NotNull.java','package org.jetbrains.annotations;public @interface NotNull{}')]:
            source=root/name;source.parent.mkdir(parents=True,exist_ok=True);source.write_text(value);sources.append(str(source))
        classes=root/'classes';subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(classes),*sources],capture_output=True,text=True,check=True)
        result=subprocess.run(['java','-ea','-cp',str(classes),'PendingProbe'],capture_output=True,text=True)
        assert (result.returncode==0)==expected,(label,result.stdout,result.stderr)
        results.append({'variant':label,'semantic_regression_passed':result.returncode==0,'expected_pass':expected})
report={'status':'PASS','scope':'Actual production scheduler body and queue with MC API stubs; budget, original task/failure identities, readiness transition, clean-section skip and visibility-range reset. Runtime GPU/terrain parity separate.','results':results}
if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
print('PENDING_REBUILDS_PRODUCTION_AND_NEGATIVE_CONTROLS_PASS',len(results))
