"""Fetch the exact public Flywheel build from its official Maven; never execute it."""
from pathlib import Path
import argparse,hashlib,urllib.request,zipfile
PIN='316ca250f19244956b5f0cd75329309ea65a77b4b8da854389b6a9222e7f427c'
URL='https://maven.createmod.net/dev/engine-room/flywheel/flywheel-forge-1.20.1/1.0.5/flywheel-forge-1.20.1-1.0.5.jar'
def fetch(output):
 if output.exists():raw=output.read_bytes()
 else:
  with urllib.request.urlopen(URL,timeout=60) as response:raw=response.read(2*1024*1024)
 assert hashlib.sha256(raw).hexdigest()==PIN,'Official reference differs from the pinned installed original; do not compile or execute it'
 if not output.exists():output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(raw)
 with zipfile.ZipFile(output) as archive:
  assert archive.testzip() is None and 'dev/engine_room/flywheel/api/backend/Engine.class' in archive.namelist()
 print('PINNED_OFFICIAL_FLYWHEEL_REFERENCE_VERIFIED',PIN)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('output',type=Path);fetch(parser.parse_args().output)
