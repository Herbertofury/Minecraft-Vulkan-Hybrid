"""Validate generated Forge 1.20.1 JAR resource-pack metadata before upload."""
import argparse,glob,json,zipfile
p=argparse.ArgumentParser();p.add_argument("jars",nargs="+");a=p.parse_args();files=[]
for pattern in a.jars:files.extend(glob.glob(pattern))
assert files,"No build artifacts matched"
for path in files:
 if path.endswith(("-sources.jar","-dev.jar")):continue
 with zipfile.ZipFile(path) as z:
  assert "pack.mcmeta" in z.namelist(),f"Missing ResourcePackInfo metadata: {path}"
  pack=json.loads(z.read("pack.mcmeta"))["pack"]
  assert type(pack["pack_format"]) is int and pack["pack_format"]==15,(path,pack)
  assert isinstance(pack["description"],(str,dict)) and pack["description"],path
 print("PASS ResourcePackInfo:",path)
