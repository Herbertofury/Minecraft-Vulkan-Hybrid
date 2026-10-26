"""Compare the replacement with the pinned real API without installing a renderer."""
from pathlib import Path
import argparse, hashlib, json, subprocess, tempfile, urllib.request, zipfile

ROOT=Path(__file__).resolve().parents[1]
URL='https://cdn.modrinth.com/data/sk9rgfiA/versions/UTbfe5d1/embeddium-0.3.31%2Bmc1.20.1.jar'
JAR_SHA='eed3d1325f2acc2fd4e69bb495e5ccb91d962126ac5330f0582ebc2a3daf47fb'
ENTRY='net/caffeinemc/mods/sodium/api/memory/MemoryIntrinsics.class'
CLASS_SHA='bb6dfc979142295b499a5dc01b9fcb515648cab59103f1f3449ea5282393dae0'

def main():
    p=argparse.ArgumentParser()
    source=p.add_mutually_exclusive_group(required=True)
    source.add_argument('--reference-jar',type=Path)
    source.add_argument('--download-reference',action='store_true')
    p.add_argument('--report',type=Path)
    a=p.parse_args()
    with tempfile.TemporaryDirectory(prefix='mvh-memory-parity-') as tmp:
        work=Path(tmp);jar=a.reference_jar
        if a.download_reference:
            request=urllib.request.Request(URL,headers={'User-Agent':'Minecraft-Vulkan-Hybrid/compatibility-regression'})
            with urllib.request.urlopen(request,timeout=60) as response:data=response.read(16*1024*1024+1)
            assert len(data)<=16*1024*1024
            jar=work/'reference.jar';jar.write_bytes(data)
        assert hashlib.sha256(jar.read_bytes()).hexdigest()==JAR_SHA,'Unreviewed reference JAR'
        with zipfile.ZipFile(jar) as z:data=z.read(ENTRY)
        assert hashlib.sha256(data).hexdigest()==CLASS_SHA,'Reference API changed'
        classes=work/'classes';original=classes/ENTRY
        original.parent.mkdir(parents=True);original.write_bytes(data)
        subprocess.run(['javac','--release','17','-cp',str(classes),'-d',str(classes),str(ROOT/'src/mvhoculuscompat/MemoryCopy.java'),str(ROOT/'tests/MemoryCopyParity.java')],check=True)
        subprocess.run(['java','-ea','-cp',str(classes),'MemoryCopyParity'],check=True,timeout=30)
    if a.report:
        a.report.parent.mkdir(parents=True,exist_ok=True)
        a.report.write_text(json.dumps({'passed':True,'reference_jar_sha256':JAR_SHA,'reference_class_sha256':CLASS_SHA,'comparisons':168,'scope':'Actual original memory API and replacement on native buffers; no game renderer installed; shader parity remains separate'},indent=2)+'\n')

if __name__=='__main__':main()
