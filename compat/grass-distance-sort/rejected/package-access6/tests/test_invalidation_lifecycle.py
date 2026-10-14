"""Model first-build, hidden/unloaded, cached, asynchronous and reload lifecycles; reject unsafe controls."""
from pathlib import Path
import argparse,json,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--report',type=Path);args=parser.parse_args()
source=(root/'src/main/java/mvhgrasscompat/InvalidationRetention.java').read_text()
variants=[('production',source,True),('in_flight_lost',source.replace('cached || inFlight','cached'),False),('cached_lost',source.replace('cached || inFlight','inFlight'),False)]
results=[]
for label,code,expected in variants:
    with tempfile.TemporaryDirectory(prefix='mvh-grass-invalidation-') as folder:
        base=Path(folder);target=base/'mvhgrasscompat/InvalidationRetention.java';target.parent.mkdir();target.write_text(code);classes=base/'classes';classes.mkdir()
        subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),str(target),str(root/'tests/InvalidationLifecycleRegression.java')],check=True,capture_output=True,text=True)
        result=subprocess.run(['java','-ea','-cp',str(classes),'InvalidationLifecycleRegression'],capture_output=True,text=True)
        assert (result.returncode==0)==expected,(label,result.stdout,result.stderr)
        results.append({'variant':label,'semantic_regression_passed':result.returncode==0,'expected_pass':expected})
report={'status':'PASS','scope':'Snapshot lifecycle model; actual mixin binding and visual/game parity still require runtime evidence; no FPS inference','results':results}
if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
print('GRASS_INVALIDATION_LIFECYCLE_NEGATIVE_CONTROLS_PASS',len(results))
