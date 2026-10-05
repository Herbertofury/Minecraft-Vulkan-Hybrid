"""Build a recoverable integration tree from verified Hari source and pinned DimThreads."""
from pathlib import Path
import argparse, hashlib, json, shutil
I=Path(__file__).resolve().parent; R=I.parents[1]
p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args();W=a.output.resolve()
assert not W.exists(), 'Use a fresh output; existing work is never overwritten'
manifest=json.loads((R/'provenance/SOURCE-RECIPE.json').read_text())['files']
for rel,digest in manifest.items():
 src=R/'source'/rel
 assert hashlib.sha256(src.read_bytes()).hexdigest()==digest,rel
 dst=W/rel;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
V=I/'vendor/dimthreads'
pin=json.loads((I/'UPSTREAM.json').read_text())
for row in pin['files']:
 assert hashlib.sha256((V/row['path']).read_bytes()).hexdigest()==row['sha256'],row['path']
shutil.copytree(V/'src/main/java',W/'forge/src/main/java',dirs_exist_ok=True)
for rel in ['assets','icon.png']:
 src=V/'src/main/resources'/rel;dst=W/'forge/src/main/resources'/rel
 if src.is_dir():shutil.copytree(src,dst,dirs_exist_ok=True)
 else:shutil.copy2(src,dst)
shutil.copy2(V/'LICENSE',W/'forge/src/main/resources/DIMTHREADS-LICENSE.txt')
for src in (I/'overlay').rglob('*'):
 if src.is_file():dst=W/src.relative_to(I/'overlay');dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
print(f'Assembled {len(manifest)} verified Hari files and pinned DimThreads into {W}')
