"""Local metadata-only recipe; upstream all-rights-reserved archives remain private."""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
SOURCE_SHA256="2ce9f56c66a97f645ff249260a750f37a74c0e4ae0b2db9cbca73bdff4f0f0e2"
def patch(source,output):
 assert source.resolve()!=output.resolve() and not output.exists()
 assert hashlib.sha256(source.read_bytes()).hexdigest()==SOURCE_SHA256,"Only inspected original accepted"
 with zipfile.ZipFile(source) as upstream:
  assert upstream.testzip() is None and len(upstream.namelist())==len(set(upstream.namelist()))
  assert not any(n.upper().endswith((".SF",".RSA",".DSA",".EC")) for n in upstream.namelist())
  assert json.loads(upstream.read("pack.mcmeta"))["pack"]["pack_format"]==15
  original=upstream.read("META-INF/mods.toml").decode("utf-8")
  pattern=r'(\[\[dependencies.enhancedcelestials2shaders\]\]\s*modId = "embeddium"\s*mandatory = )true'
  changed,count=re.subn(pattern,r"\1false",original);assert count==1
  with zipfile.ZipFile(output,"w") as patched:
   for entry in upstream.infolist():patched.writestr(entry,changed.encode("utf-8") if entry.filename=="META-INF/mods.toml" else upstream.read(entry))
 with zipfile.ZipFile(source) as a,zipfile.ZipFile(output) as b:
  assert a.namelist()==b.namelist()
  assert [n for n in a.namelist() if not n.endswith("/") and a.read(n)!=b.read(n)]==["META-INF/mods.toml"]
 return {"source_sha256":SOURCE_SHA256,"output_sha256":hashlib.sha256(output.read_bytes()).hexdigest(),"changed_entries":["META-INF/mods.toml"],"code_assets_licenses_equal":True,"shader_parity_accepted":False}
if __name__=="__main__":
 p=argparse.ArgumentParser();p.add_argument("source",type=Path);p.add_argument("output",type=Path);a=p.parse_args();print(json.dumps(patch(a.source,a.output),indent=2))
