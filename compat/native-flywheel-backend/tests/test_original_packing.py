from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tempfile
P=Path(__file__).resolve().parent.parent
ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);a=ap.parse_args()
source=(P/'src/main/java/mvhflywheelbackend/NativeWorldRenderer.java').read_text()
start=source.index('    static int packOriginalInstances(');end=source.index('    void render(',start)
method=source[start:end].replace('static int packOriginalInstances','public static int packOriginalInstances')
variants={
 'deleted_slots_included':method.replace('h==null||h.deleted','h==null'),
 'logical_visibility_ignored':method.replace('h.visible&&',''),
 'sparse_target_index_wrong':method.replace('targets+visible*4L,i','targets+visible*4L,visible'),
 'writer_stride_wrong':method.replace('data+i*(long)stride','data+i*4L'),
}
proof={'scope':'Actual production packing method; independently expected original sparse storage, logical visibility, duplicate selection, page masks, per-slot writer addresses and invalid selections. Native GPU clipping/drawing separately tested; no FPS inferred.','source_sha256':hashlib.sha256(source.encode()).hexdigest(),'positive':None,'negative_controls':{}}
suffix='.exe' if os.name=='nt' else ''
def java_tool(name):
    home=os.environ.get('JAVA_HOME');q=Path(home)/'bin'/(name+suffix) if home else None
    return str(q) if q and q.is_file() else shutil.which(name) or name
for name,body in [('production',method),*variants.items()]:
    assert name=='production' or body!=method
    with tempfile.TemporaryDirectory(prefix='mvh-original-packing-') as folder:
        d=Path(folder);files={
          'mvhflywheelbackend/NativeWorldRenderer.java':'package mvhflywheelbackend;import java.util.*;import org.lwjgl.system.MemoryUtil;public class NativeWorldRenderer{'+body+'}',
          'mvhflywheelbackend/NativeEngine.java':'''package mvhflywheelbackend;import java.util.*;public class NativeEngine{
            public static class Handle{public boolean visible,deleted;public int payload;}
            public static class NativeInstancer<T>{public final ArrayList<Handle> handles=new ArrayList<>();public final Map<Integer,Long> written=new HashMap<>();public void write(int i,long pointer){if(handles.get(i)==null||handles.get(i).deleted)throw new AssertionError("Deleted writer invoked");written.put(i,pointer);}}
          }''',
          'org/lwjgl/system/MemoryUtil.java':'''package org.lwjgl.system;import java.util.*;public class MemoryUtil{static final Map<Long,Integer> storage=new HashMap<>();public static void reset(){storage.clear();}public static int memGetInt(long address){return storage.getOrDefault(address,0);}public static void memPutInt(long address,int value){if(address<10000||address>=40000||(address&3)!=0)throw new AssertionError("Invalid native storage address");storage.put(address,value);}}''',
        }
        for rel,text in files.items():f=d/rel;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
        r=subprocess.run([java_tool('javac'),'-encoding','UTF-8','-d',str(d),*[str(d/f) for f in files],str(P/'tests/OriginalPackingRegression.java')],capture_output=True,text=True);assert r.returncode==0,r.stderr
        r=subprocess.run([java_tool('java'),'-ea','-cp',str(d),'OriginalPackingRegression'],capture_output=True,text=True)
        if name=='production':assert r.returncode==0,r.stderr;proof['positive']=r.stdout.strip()
        else:assert r.returncode!=0 and 'AssertionError' in r.stderr,name;proof['negative_controls'][name]='rejected'
if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(proof,indent=2)+'\n')
print(json.dumps(proof,indent=2))
