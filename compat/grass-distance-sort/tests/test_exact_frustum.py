from pathlib import Path
import argparse,json,os,shutil,subprocess,tempfile
P=Path(__file__).resolve().parent.parent
a=argparse.ArgumentParser();a.add_argument('--joml',required=True,type=Path);args=a.parse_args()
j=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac')).parent
suffix='.exe' if os.name=='nt' else ''
s=(P/'src/main/java/mvhgrasscompat/ExactFrustumCache.java').read_text();test=P/'tests/ExactFrustumCacheRegression.java'
variants={'matrix_invalidation_removed':s.replace('changed|=matrix[i]!=Float.floatToRawIntBits(values[i]);','changed|=false;'),'camera_invalidation_removed':s.replace('changed|=camera[0]!=Double.doubleToRawLongBits(x)||camera[1]!=Double.doubleToRawLongBits(y)||camera[2]!=Double.doubleToRawLongBits(z);','changed|=false;'),'sixth_bounds_key_removed':s.replace('&&bounds[p+5]==ff','').replace('hash=hash*31+ff;','hash=hash*31;')}
proof={'scope':'Actual bounded production visibility cache against independent JOML projection oracle; not FPS or full-mod acceptance.','positive':None,'negative_controls':{}}
for name,source in [('production',s),*variants.items()]:
    assert name=='production' or source!=s
    with tempfile.TemporaryDirectory(prefix='mvh-frustum-') as d:
        d=Path(d);f=d/'ExactFrustumCache.java';f.write_text(source);cp=str(args.joml.resolve())
        compiled=subprocess.run([str(j/('javac'+suffix)),'-cp',cp,'-d',str(d),str(f),str(test)],capture_output=True,text=True);assert compiled.returncode==0,compiled.stderr
        r=subprocess.run([str(j/('java'+suffix)),'-cp',str(d)+os.pathsep+cp,'ExactFrustumCacheRegression'],capture_output=True,text=True)
        if name=='production':assert r.returncode==0,r.stderr;proof['positive']=r.stdout.strip()
        else:assert r.returncode!=0 and 'Visibility cache control' in r.stderr,name;proof['negative_controls'][name]='rejected'
print(json.dumps(proof,indent=2))
