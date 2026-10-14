"""Fetch the public LGPL Elementa shaders used by the original installed UI."""
from pathlib import Path
import argparse,hashlib,json,urllib.request

PIN='54bef9fc59648c44529ddd49d82d3216f2f437d6'
FILES=[('000-circle.fsh','circle.fsh','259cff0e9e440212db1714c3e28ca39e2d632d9c328a7f4e3275f81a2739f9b7'),
       ('001-font.fsh','font.fsh','47efc60d7827351f0627c8191cffc5fba9d0c419c336a62702a380ff64c99ade'),
       ('002-font.vsh','font.vsh','24fb624551cb215701d44750a9a9bc224df429f3fa1832644af2948d1d453952'),
       ('003-rect.vsh','rect.vsh','716c2484e10c915b45f45c3bdf34e7209b490d9d1fbd28885262eb44e49fe01d'),
       ('004-rounded_rect.fsh','rounded_rect.fsh','62998adcadedb6287ffbaa94d139c8786388bd5c57b64b288a2fadf243cc5d62')]

def main():
    ap=argparse.ArgumentParser();ap.add_argument('output',type=Path);a=ap.parse_args()
    assert not a.output.exists();a.output.mkdir(parents=True)
    rows=[]
    for local,name,digest in FILES:
        url=f'https://raw.githubusercontent.com/EssentialGG/Elementa/{PIN}/src/main/resources/shaders/{name}'
        with urllib.request.urlopen(url,timeout=45) as response:raw=response.read()
        assert hashlib.sha256(raw).hexdigest()==digest,name
        p=a.output/local;p.write_bytes(raw)
        rows.append({'path':'shaders/'+name,'local':str(p.resolve()),'url':url,'sha256':digest,'bytes':len(raw)})
    with urllib.request.urlopen(f'https://raw.githubusercontent.com/EssentialGG/Elementa/{PIN}/LICENSE',timeout=45) as response:license_bytes=response.read()
    assert hashlib.sha256(license_bytes).hexdigest()=='e3a994d82e644b03a792a930f574002658412f62407f5fee083f2555c5f23118'
    (a.output/'LICENSE-Elementa').write_bytes(license_bytes)
    (a.output/'INPUTS.json').write_text(json.dumps({'repository':'EssentialGG/Elementa','commit':PIN,'license':'LGPL-3.0','shaders':rows},indent=2)+'\n',encoding='utf8')
    print('PUBLIC_ELEMENTA_ORIGINAL_INPUTS_HASH_MATCH',len(rows))
if __name__=='__main__':main()
