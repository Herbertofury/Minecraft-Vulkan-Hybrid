from pathlib import Path
import argparse,json,subprocess,tempfile
P=Path(__file__).resolve().parent.parent;ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);a=ap.parse_args();s=(P/'src/main/java/mvhgrasscompat/FirstBuildEligibility.java').read_text()
variants={'cached_refresh_dropped':s.replace('!cached&&visibilityKnown','visibilityKnown'),'visible_first_build_dropped':s.replace('&&!visible','&&visible')}
proof={'scope':'Actual pure early-eligibility condition against independent original queue/async completion/world mutation/visibility/unloaded-region/budget trace; actual mixin and visuals separately tested. No FPS inferred.','positive':None,'negative_controls':{}}
for name,source in [('production',s),*variants.items()]:
    assert name=='production' or source!=s
    with tempfile.TemporaryDirectory(prefix='mvh-first-build-') as d:
        d=Path(d);f=d/'FirstBuildEligibility.java';f.write_text(source);r=subprocess.run(['javac','-encoding','UTF-8','-d',str(d),str(f),str(P/'tests/FirstBuildEligibilityRegression.java')],capture_output=True,text=True);assert r.returncode==0,r.stderr
        r=subprocess.run(['java','-ea','-cp',str(d),'FirstBuildEligibilityRegression'],capture_output=True,text=True)
        if name=='production':assert r.returncode==0,r.stderr;proof['positive']=r.stdout.strip()
        else:assert r.returncode!=0 and 'AssertionError' in r.stderr,name;proof['negative_controls'][name]='rejected'
if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(proof,indent=2)+'\n')
print(json.dumps(proof,indent=2))
