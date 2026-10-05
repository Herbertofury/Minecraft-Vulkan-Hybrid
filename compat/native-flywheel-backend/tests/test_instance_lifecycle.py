"""Compile actual instance/context/pruning methods against a small checked API harness.
Ownership, hidden/deleted children and concurrent stable handles; no GPU/FPS claim.
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
ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);a=ap.parse_args()
source=Path(__file__).resolve().parents[1]/'src/main/java/mvhflywheelbackend/NativeEngine.java';s=source.read_text()
methods='\n'.join(block(s,x) for x in ['@SuppressWarnings("unchecked") private <I extends Instance> NativeInstancer<I> instancer(','class Context implements VisualizationContext','final class Embedding extends Context','static final class NativeInstancer','static final class Handle'])
prune=block(s,'for(var it=instancers.values().iterator();it.hasNext();')
prefix='''import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class NativeEngine {
static final Object MUTATIONS=new Object();final Map<Key,NativeInstancer<?>> instancers=new LinkedHashMap<>();boolean deleted,meshReap;BlockPos origin=new BlockPos();final Context root=new Context(null,null);
private record Key(Context context,InstanceType<?> type,Model model,int bias){}void live(){if(deleted)throw new IllegalStateException("Deleted engine");}
interface Instance{InstanceType<?> type();InstanceHandle handle();}
interface InstanceHandle{void setChanged();void setDeleted();void setVisible(boolean value);boolean isVisible();}
interface Instancer<I extends Instance>{I createInstance();void stealInstance(I instance);}
interface InstanceType<I extends Instance>{I create(InstanceHandle handle);Layout layout();Writer<I> writer();}
interface Writer<I extends Instance>{void write(long pointer,I instance);}record Layout(int byteSize){}record Model(int id){}
interface InstancerProvider{<I extends Instance> Instancer<I> instancer(InstanceType<I> type,Model model,int bias);}
interface VisualizationContext{InstancerProvider instancerProvider();Vec3i renderOrigin();VisualEmbedding createEmbedding(Vec3i origin);}
interface VisualEmbedding extends VisualizationContext{void transforms(Matrix4fc pose,Matrix3fc normal);void delete();}
static class Vec3i{}static class BlockPos extends Vec3i{}interface Matrix4fc{}interface Matrix3fc{}
static class Matrix4f implements Matrix4fc{Matrix4f set(Matrix4fc m){return this;}void mul(Matrix4f m){}}
static class Matrix3f implements Matrix3fc{Matrix3f set(Matrix3fc m){return this;}void mul(Matrix3f m){}}
static int checks;static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}static void rejected(Runnable action,String name){try{action.run();throw new AssertionError("Not rejected: "+name);}catch(IllegalStateException expected){checks++;}}
record TestInstance(InstanceType<?> type,InstanceHandle handle)implements Instance{}
static final InstanceType<TestInstance> TYPE=new InstanceType<>(){public TestInstance create(InstanceHandle h){check(Thread.holdsLock(MUTATIONS),"Instance creation lost mutation ownership");return new TestInstance(this,h);}public Layout layout(){return new Layout(4);}public Writer<TestInstance> writer(){return (p,i)->{};}};
'''
test='''
void pruneChildren(){synchronized(MUTATIONS){PRUNE}}
public static void main(String[] args)throws Exception{
NativeEngine e=new NativeEngine();Context parent=(Context)e.root.createEmbedding(new Vec3i());Context child=(Context)parent.createEmbedding(new Vec3i());Model a=new Model(1),b=new Model(2);
NativeInstancer<TestInstance> first=e.instancer(child,TYPE,a,0);TestInstance hidden=first.createInstance();hidden.handle().setVisible(false);parent.delete();e.pruneChildren();check(e.instancers.size()==1&&first.hasLive()&&first.count()==0,"Hidden live child wrongly retired");
check(e.instancer(child,TYPE,a,0)==first,"Existing child instancer ceased functioning after parent deletion");TestInstance existing=first.createInstance();check(existing.handle().isVisible(),"Existing child cannot create before retirement");rejected(()->e.instancer(child,TYPE,b,0),"New instancer in deleted parent");rejected(()->child.createEmbedding(new Vec3i()),"New nested embedding after parent delete");
hidden.handle().setDeleted();existing.handle().setDeleted();e.pruneChildren();check(e.instancers.isEmpty()&&e.meshReap,"Deleted empty child retained");rejected(first::createInstance,"Retired child recreated");
NativeInstancer<TestInstance> left=e.instancer(e.root,TYPE,a,0),right=e.instancer(e.root,TYPE,b,0);TestInstance moved=left.createInstance();InstanceHandle stable=moved.handle();right.stealInstance(moved);check(left.count()==0&&right.count()==1&&moved.handle()==stable,"Stable handle lost during steal");stable.setVisible(false);check(right.hasLive()&&right.count()==0,"Hidden transferred instance disappeared");stable.setVisible(true);stable.setChanged();check(right.count()==1,"Visibility not restored");stable.setDeleted();right.prune();check(right.count()==0&&!right.hasLive(),"Deleted handle retained");
var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);List<Future<List<TestInstance>>> jobs=new ArrayList<>();for(int worker=0;worker<8;worker++)jobs.add(pool.submit(()->{start.await();List<TestInstance> local=new ArrayList<>();for(int n=0;n<600;n++)local.add(left.createInstance());return local;}));start.countDown();List<TestInstance> all=new ArrayList<>();for(var job:jobs)all.addAll(job.get());pool.shutdown();check(left.count()==4800,"Concurrent instance count lost");Set<InstanceHandle> unique=Collections.newSetFromMap(new IdentityHashMap<>());for(var i:all)unique.add(i.handle());check(unique.size()==4800,"Concurrent handles aliased");for(var i:all)i.handle().setDeleted();left.prune();check(!left.hasLive()&&left.count()==0,"Concurrent deletion retained handles");for(int n=0;n<200;n++)left.createInstance();check(left.count()==200&&left.handles.size()==4800,"Sparse free slots not reused");left.clear();check(left.count()==0&&left.handles.isEmpty()&&left.free.isEmpty(),"Render-origin clear retained stale handles");
System.out.println("ACTUAL_NATIVE_INSTANCE_LIFECYCLE_CONTROLS "+checks);
}}
'''.replace('PRUNE',prune)
code=prefix+methods+test
variants={'hidden_live_retired':code.replace('i.context.closedChain()&&!i.hasLive()','i.context.closedChain()&&i.count()==0'),'mutation_lock_removed':code.replace('synchronized(MUTATIONS)','synchronized(new Object())'),'retired_instancer_recreation':code.replace('i.retired=true;','i.retired=false;'),'new_deleted_child_allowed':code.replace('context.canCreate();NativeInstancer<I> created','NativeInstancer<I> created')}
proof={'scope':'Actual production context, instance, handle and child-pruning CPU methods; checked API harness and eight concurrent workers. Not GPU/visual/FPS evidence.','source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'positive':None,'negative_controls':{}}
for name,value in [('production',code),*variants.items()]:
    assert name=='production' or value!=code
    with tempfile.TemporaryDirectory(prefix='mvh-native-instance-') as d:
        d=Path(d);f=d/'NativeEngine.java';f.write_text(value);r=subprocess.run(['javac','-encoding','UTF-8','-d',str(d),str(f)],capture_output=True,text=True);assert r.returncode==0,r.stderr
        r=subprocess.run(['java','-ea','-cp',str(d),'NativeEngine'],capture_output=True,text=True,timeout=25)
        if name=='production':assert r.returncode==0,r.stderr;proof['positive']=r.stdout.strip()
        else:assert r.returncode!=0 and 'AssertionError' in r.stderr,name;proof['negative_controls'][name]='rejected'
if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(proof,indent=2)+'\n')
print(json.dumps(proof,indent=2))
