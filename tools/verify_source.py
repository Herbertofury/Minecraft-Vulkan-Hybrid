"""Verify every accepted source file; never omit Java packages named build."""
from pathlib import Path
import hashlib,json,sys
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'provenance/SOURCE-RECIPE.json').read_text(encoding='utf-8'))
missing=[];changed=[]
for name,expected in manifest['files'].items():
    p=root/'source'/name
    if not p.is_file():missing.append(name)
    elif hashlib.sha256(p.read_bytes()).hexdigest()!=expected:changed.append(name)
print(json.dumps({'checked':len(manifest['files']),'missing':missing,'changed':changed},indent=2))
sys.exit(bool(missing or changed))
