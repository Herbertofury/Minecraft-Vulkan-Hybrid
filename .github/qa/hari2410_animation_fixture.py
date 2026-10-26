#!/usr/bin/env python3
"""Equivalent-work high-resolution texture-animation workload from original vanilla pixels."""
import hashlib
import io
import json
import urllib.request
import zipfile
from pathlib import Path
from PIL import Image

client = Path('compat-mod-cache/minecraft-1.20.1-client.jar')
client.parent.mkdir(exist_ok=True)
if not client.is_file():
    client.write_bytes(urllib.request.urlopen('https://piston-data.mojang.com/v1/objects/0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838/client.jar',timeout=180).read())
assert hashlib.sha1(client.read_bytes()).hexdigest() == '0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838'
folder = Path('pmc-game/resourcepacks'); folder.mkdir(exist_ok=True)
target = folder / 'harimt-animation-8x.zip'
records=[]
with zipfile.ZipFile(client) as original, zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as pack:
    pack.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Hari QA: original vanilla animated pixels, nearest-neighbor 8x'}}))
    for name in sorted(original.namelist()):
        if not name.startswith('assets/minecraft/textures/block/') or not name.endswith('.png.mcmeta'): continue
        meta = original.read(name)
        if 'animation' not in json.loads(meta): continue
        png = name[:-7]; pixels = original.read(png)
        image = Image.open(io.BytesIO(pixels)); size=image.size
        image = image.resize((size[0]*8,size[1]*8),Image.Resampling.NEAREST)
        stream=io.BytesIO();image.save(stream,format='PNG')
        pack.writestr(png,stream.getvalue());pack.writestr(name,meta)
        records.append({'texture':png,'original_sha256':hashlib.sha256(pixels).hexdigest(),
                        'metadata_unchanged_sha256':hashlib.sha256(meta).hexdigest(),
                        'original_size':size,'benchmark_size':image.size})
assert records
Path('production-qa/animation-fixture.json').write_text(json.dumps({'not_a_default_gameplay_setting':True,
    'same_fixture_both_variants':True,'client_sha1':'0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838',
    'resource_pack_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'textures':records},indent=2)+'\n')
options=Path('pmc-game/options.txt')
options.write_text(options.read_text()+'resourcePacks:["vanilla","mod_resources","file/harimt-animation-8x.zip"]\n')
