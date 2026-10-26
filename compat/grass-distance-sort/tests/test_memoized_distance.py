"""Compile production sort code and prove unsafe camera/input/failure variants fail."""
from pathlib import Path
import argparse, json, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
ap=argparse.ArgumentParser();ap.add_argument('--report',type=Path);args=ap.parse_args()
source=(root/'src/main/java/mvhgrasscompat/MemoizedDistanceSort.java').read_text()
variants=[('production',source,True),
          ('camera_check_removed',source.replace('&& bx == cameraX && by == cameraY && bz == cameraZ',''),False),
          ('input_sequence_check_removed',source.replace('&& Arrays.equals(values, 0, count, input, 0, count)','&& true'),False),
          ('failed_sort_invalidation_removed',source.replace('// A failed distance evaluation cannot make a new input hit an older result.\n        valid = false;','// Deliberately unsafe negative control: prior result remains valid.'),False)]
results=[]
for label,text,expected in variants:
    assert expected or text!=source
    with tempfile.TemporaryDirectory(prefix='mvh-grass-sort-') as directory:
        temp=Path(directory);src=temp/'mvhgrasscompat/MemoizedDistanceSort.java';src.parent.mkdir();src.write_text(text)
        classes=temp/'classes';classes.mkdir()
        subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),str(root/'src/main/java/mvhgrasscompat/DistanceSort.java'),str(src),str(root/'tests/MemoizedDistanceSortRegression.java')],check=True,capture_output=True,text=True)
        test=subprocess.run(['java','-ea','-cp',str(classes),'MemoizedDistanceSortRegression'],capture_output=True,text=True)
        passed=test.returncode==0;assert passed==expected,(label,test.stdout,test.stderr)
        results.append({'variant':label,'semantic_regression_passed':passed,'expected_pass':expected})
result={'status':'PASS','scope':'Production memoized sort compared with independent JDK stable order; three unsafe controls must fail; no game/FPS inference','results':results}
if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n')
print('GRASS_SORT_CAMERA_INPUT_FAILURE_NEGATIVE_CONTROLS_PASS',len(results))
