from pathlib import Path
import argparse,hashlib,json,psutil,re,sqlite3,subprocess,zipfile
parser=argparse.ArgumentParser();parser.add_argument('--version',required=True);parser.add_argument('--stage',required=True);args=parser.parse_args()
assert re.fullmatch(r'1\.0\.[0-9]+',args.version) and re.fullmatch(r'[A-Za-z0-9_-]+',args.stage)
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative')
row=json.loads((C/'instance.json').read_text());root=Path(row['path'])
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:assert all(not psutil.pid_exists(r[0]) for r in db.execute('select pid from processes where instance_id=?',(row['id'],)))
source=W/f'noxviola-control/build/libs/mvh-noxviola-control-{args.version}.jar';digest=hashlib.sha256(source.read_bytes()).hexdigest()
with zipfile.ZipFile(source) as z:assert json.loads(z.read('pack.mcmeta'))['pack']['pack_format']==15 and 'mvhpackbench/GrassNativeSmoke.class' in z.namelist()
input=C/'stage-inputs'/f'{args.stage}-controller-{args.version}.json';assert not input.exists()
input.write_text(json.dumps({'stage':args.stage,'add':[{'path':str(source),'sha256':digest}],'disable':['mods/'+f.name for f in (root/'mods').glob('mvh-noxviola-control-*.jar')]},indent=2)+'\n')
with (C/f'{args.stage}-controller-{args.version}-install-private.log').open('w') as log:subprocess.run([str(C.parent/'tools/mvh_pack_cli.exe'),'add-cumulative',str(input)],stdout=log,stderr=log,cwd=W,check=True,timeout=120)
print('OWNED_GRASS_SMOKE_CONTROLLER',source.name,digest)
