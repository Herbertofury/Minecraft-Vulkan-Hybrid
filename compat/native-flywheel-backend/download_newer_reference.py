"""Download checksum-pinned official newer Flywheel binary/sources for the native port."""
from pathlib import Path
import argparse, concurrent.futures, hashlib, urllib.request

BASE='https://maven.createmod.net/dev/engine-room/flywheel/flywheel-forge-1.20.1/1.0.6-beta-266/'
NAME='flywheel-forge-1.20.1-1.0.6-beta-266'
PINS={'binary':'4e3667f3ddf413425eaf85a2380fb65496cf8c7d80a738e279d295b6c02d8ee5',
      'sources':'9f45eeb4ba8608eec6ab13994a06a755eedb2753cf4fd078de5f371ed38bb6ca'}

def download(binary,sources):
    assert binary.resolve()!=sources.resolve() and not binary.exists() and not sources.exists()
    files=[NAME+'.jar',NAME+'-sources.jar']
    def read(name):
        with urllib.request.urlopen(BASE+name,timeout=45) as response:
            return response.read()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        data=list(pool.map(read,files))
    for key,raw in zip(['binary','sources'],data):
        assert hashlib.sha256(raw).hexdigest()==PINS[key], 'Unexpected official '+key
    for p,raw in zip([binary,sources],data):
        p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
    print('EXACT_OFFICIAL_NEWER_FLYWHEEL_BINARY_AND_SOURCES_VERIFIED')

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('binary',type=Path);ap.add_argument('sources',type=Path)
    a=ap.parse_args();download(a.binary,a.sources)
