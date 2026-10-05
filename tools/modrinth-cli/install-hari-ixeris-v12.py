"""Install only the just-built reviewed integration into the exact inactive owned clone."""
from pathlib import Path
import hashlib,json,psutil,shutil,sqlite3,subprocess,zipfile
W=Path(__file__).resolve().parent;C=Path('C:/Users/Owner/Desktop/Minecraft Vulkan Hybrid/benchmarks/cumulative');row=json.loads((C/'instance.json').read_text());root=Path(row['path'])
assert row['id']=='local:b6985be9-ded6-4a96-85cd-5a2548b1d900'
assert json.loads((root/'MVH-CUMULATIVE-IDENTITY.json').read_text())=={'task':'mvh-cumulative-20261005','id':row['id'],'source':'local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3'}
with sqlite3.connect('file:C:/Users/Owner/AppData/Roaming/ModrinthApp/app.db?mode=ro',uri=True) as db:assert all(not psutil.pid_exists(x[0]) for x in db.execute('select pid from processes where instance_id=?',(row['id'],)))
name='harimt-forge-1.20.1-2.4.11-noxviola.12-dimensions-vulkan-hybrid-all.jar';source=W/'hari-ixeris-native-build/forge/build/libs'/name
with zipfile.ZipFile(source) as z:
    assert json.loads(z.read('pack.mcmeta'))['pack']['pack_format']==15
    assert b'6cc7e1bfad6a2e82983a41535accf919a1027ca7087f1e6fcc955df22f7d91e1' in z.read('net/vulkanmod/compat/InactiveIxerisMacOsGlAudit.class')
    assert b'2.4.11-vulkan-gate-v12-audited-inactive-platform' in z.read('net/vulkanmod/compat/UniversalRendererGate.class')
    assert b'2.4.11-noxviola.12-dimensions-vulkan-hybrid' in z.read('META-INF/mods.toml')
digest=hashlib.sha256(source.read_bytes()).hexdigest();target=W/'hari-dimensions-build/forge/build/libs'/name;assert not target.exists();shutil.copy2(source,target);assert hashlib.sha256(target.read_bytes()).hexdigest()==digest
receipt=W/'inventory/noxviola/ixeris-native-v12-build-manifest.json';assert not receipt.exists();receipt.write_text(json.dumps({'file':name,'sha256':digest,'build':'Java 17, original pinned source + DimThreads + reviewed .12 overlay; local Forge build passed','resource_pack_format':15,'native_game_accepted':False,'source_code_gate_control_proofs':'ixeris-platform-gate-proof.json; modernfix-after-ixeris-gate-proof.json; grass-after-ixeris-gate-proof.json'},indent=2)+'\n')
stage='015-ixeris-native-v12-install';p=C/'stage-inputs'/(stage+'.json');assert not p.exists();p.write_text(json.dumps({'stage':stage,'add':[{'path':str(target),'sha256':digest}],'enable':[],'disable':['mods/'+p.name for p in (root/'mods').glob('harimt-*.jar')]},indent=2)+'\n')
with (C/(stage+'-private.log')).open('w') as log:subprocess.run([str(C.parent/'tools/mvh_pack_cli.exe'),'add-cumulative',str(p)],cwd=W,stdout=log,stderr=log,check=True,timeout=120)
print('OWNED_REVIEWED_HARI_V12_INSTALLED',digest)
