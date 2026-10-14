"""Private native Flywheel backend fork inside original Create. Original API/lib/assets stay exact."""
from pathlib import Path
import argparse,copy,hashlib,io,json,re,struct,zipfile
H=Path(__file__).resolve().parent
BASE='dev/engine_room/flywheel/backend/'
def sha(b):return hashlib.sha256(b).hexdigest()
def references(raw):
 assert raw[:4]==b'\xca\xfe\xba\xbe';count=struct.unpack_from('>H',raw,8)[0];offset=10;i=1;utf={};classes=[]
 while i<count:
  tag=raw[offset];offset+=1
  if tag==1:
   n=struct.unpack_from('>H',raw,offset)[0];offset+=2;utf[i]=raw[offset:offset+n].decode('utf8',errors='replace');offset+=n
  elif tag==7:classes.append(struct.unpack_from('>H',raw,offset)[0]);offset+=2
  elif tag in (3,4,9,10,11,12,17,18):offset+=4
  elif tag in (5,6):offset+=8;i+=1
  elif tag in (8,16,19,20):offset+=2
  elif tag==15:offset+=3
  else:raise AssertionError(tag)
  i+=1
 refs={utf[k] for k in classes if utf[k].startswith('dev/engine_room/flywheel/')}
 for s in utf.values():refs.update(re.findall(r'L(dev/engine_room/flywheel/[^;<>]+);',s))
 return refs
def build(original,backend,output):
 assert not output.exists() and output.resolve()!=original.resolve()
 before=original.read_bytes();parent_manifest=json.loads(original.with_suffix('.manifest.json').read_text());assert sha(before)==parent_manifest['output_sha256']
 entry='META-INF/jarjar/flywheel-forge-1.20.1-1.0.5.jar'
 with zipfile.ZipFile(io.BytesIO(before)) as outer,zipfile.ZipFile(backend) as port:
  nested=outer.read(entry);assert sha(nested)=='316ca250f19244956b5f0cd75329309ea65a77b4b8da854389b6a9222e7f427c'
  with zipfile.ZipFile(io.BytesIO(nested)) as old:
   replacements={n:port.read(n) for n in port.namelist() if n.endswith('.class')}
   roots=['BackendConfig','BackendDebugFlags','FlwBackend','FlwBackendXplat','FlwBackendXplatImpl','MaterialShaderIndices','Samplers','SkyLightSectionStorageExtension','gl/GlTextureUnit','compile/core/ShaderException',
    'compile/LayoutInterpreter','compile/component/InstanceAssemblerComponent','compile/component/InstanceStructComponent',
    'compile/component/SsboInstanceComponent','compile/component/UberShaderComponent','compile/component/StringSubstitutionComponent',
    'engine/LightLut','engine/LightDataCollector','engine/AbstractArena','engine/CpuArena','engine/MaterialEncoder','engine/TextureBinder']
   prefixes=['glsl/','engine/uniform/','mixin/','util/']
   def keep(n):
    if not n.startswith(BASE) or not n.endswith('.class'):return True
    p=n[len(BASE):];return any(p.startswith(x) for x in prefixes) or any(p==x+'.class' or p.startswith(x+'$') for x in roots) or n in replacements
   kept={n:old.read(n) for n in old.namelist() if keep(n)};removed=sorted(set(old.namelist())-set(kept));kept.update(replacements)
   config_name='flywheel.backend.mixins.json';config=json.loads(kept[config_name]);original_mixins=list(config['client'])
   for name in ['NativeBuildTaskMixin','NativeWorldReloadMixin']:
    assert BASE+'mixin/'+name+'.class' in replacements and name not in config['client'];config['client'].append(name)
   assert config['client'][:len(original_mixins)]==original_mixins
   kept[config_name]=(json.dumps(config,indent=2)+'\n').encode()
   missing={}
   for n,b in kept.items():
    if n.endswith('.class'):
     refs=sorted(r for r in references(b) if r+'.class' not in kept)
     if refs:missing[n]=refs
   assert not missing,'Retained code references removed backend types: '+json.dumps(missing,indent=2)
   pack=kept.get('pack.mcmeta');assert pack is not None
   metadata=json.loads(pack);metadata['pack']['pack_format']=15;kept['pack.mcmeta']=(json.dumps(metadata,indent=2)+'\n').encode()
   toml=kept['META-INF/mods.toml'];toml,count=re.subn(rb'(?m)^license\s*=\s*"MIT"\s*$',b'license = "MIT (upstream); GPL-3.0-only (native backend)"\n',toml);assert count==1;kept['META-INF/mods.toml']=toml
   manifest={'original_create_sha256':parent_manifest['source_sha256'],'ponder_repaired_create_sha256':sha(before),'original_flywheel_sha256':sha(nested),
    'native_backend_sha256':sha(backend.read_bytes()),'removed_legacy_renderer_classes':removed,'changed_classes':sorted(replacements),
    'all_original_public_api_lib_and_assets_preserved':True,'frontend_changes':'Only backend debug-info type probe and device/instance counters are adapted to the new real engine; visualization plans, managers, content, registration and events are unchanged.','resource_pack_format':15,'runtime_accepted':False,
    'scope':'Native-only Flywheel Engine implementation replaces the internal OpenGL backend. Original visuals, model baking, instance writers, tasks, public API, content and resources are preserved. Shader/material/light/embedding/crumbling lifecycle is implemented. Full OIT/depth-pyramid optimization and active Oculus/Iris remain separate acceptance work.'}
   for n in old.namelist():
    if n.startswith(('dev/engine_room/flywheel/api/','dev/engine_room/flywheel/lib/','dev/engine_room/flywheel/impl/','assets/')) and n not in replacements:assert kept[n]==old.read(n),n
   kept['META-INF/MVH-NATIVE-FLYWHEEL.json']=(json.dumps(manifest,indent=2)+'\n').encode()
   kept['META-INF/MVH-NATIVE-FLYWHEEL-GPL.txt']=(H/'LICENSE').read_bytes();kept['META-INF/MVH-NATIVE-FLYWHEEL-UPSTREAM-MIT.txt']=(H/'FLYWHEEL-LICENSE-MIT.txt').read_bytes()
   buffer=io.BytesIO()
   with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED) as new:
    for n in sorted(kept):
     info=zipfile.ZipInfo(n,(2026,10,5,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;new.writestr(info,kept[n])
   fixed=buffer.getvalue();output.parent.mkdir(parents=True,exist_ok=True)
   with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as new:
    for info in outer.infolist():new.writestr(copy.copy(info),fixed if info.filename==entry else outer.read(info.filename))
    new.writestr('META-INF/MVH-NATIVE-FLYWHEEL.json',json.dumps(manifest,indent=2))
   with zipfile.ZipFile(output) as verify:
    for n in outer.namelist():assert verify.read(n)==(fixed if n==entry else outer.read(n)),n
   manifest['output_sha256']=sha(output.read_bytes());manifest['native_flywheel_sha256']=sha(fixed);output.with_suffix('.manifest.json').write_bytes((json.dumps(manifest,indent=2)+'\n').encode());print('PRIVATE_NATIVE_CREATE_BACKEND_BUILT',manifest['output_sha256'],'PUBLIC_API_CONTENT_PRESERVED')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('original',type=Path);p.add_argument('backend',type=Path);p.add_argument('output',type=Path);a=p.parse_args();build(a.original,a.backend,a.output)
