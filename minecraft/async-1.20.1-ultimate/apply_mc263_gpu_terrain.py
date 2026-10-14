#!/usr/bin/env python3
"""Apply HariMultiThread Ultimate 2.3.0 GPU-terrain layer to certified 2.2.0 source."""
from __future__ import annotations

from pathlib import Path
import base64
import gzip
import hashlib
import subprocess
import sys
import tempfile

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_mc263_gpu_terrain.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
parts_dir = here / "mc263_gpu_terrain_patch"
props = root / "gradle.properties"

if not props.is_file() or "version=2.2.0-noxviola.1" not in props.read_text(encoding="utf-8"):
    raise SystemExit("source drift: expected certified 2.2.0-noxviola.1 reconstructed base")

parts = sorted(parts_dir.glob("part*.b64"))
if [p.name for p in parts] != [f"part{i:02d}.b64" for i in range(5)]:
    raise SystemExit("Hari 2.3 patch chunks are incomplete")

b64 = "".join(p.read_text(encoding="ascii").strip() for p in parts).encode("ascii")
if hashlib.sha256(b64).hexdigest() != "2c2aba658d466bb9c67bdb3a759bd40e2fd432dc62bbec08d35973a587017a0c":
    raise SystemExit("Hari 2.3 patch base64 checksum mismatch")

gz = base64.b64decode(b64, validate=True)
if hashlib.sha256(gz).hexdigest() != "edd6e537aacd5cae1688e14ea50cf9ced4f9389ec9f152504163e8864296826a":
    raise SystemExit("Hari 2.3 patch gzip checksum mismatch")

patch_bytes = gzip.decompress(gz)
if hashlib.sha256(patch_bytes).hexdigest() != "1ba5a98d386b00eab0bfe1351cd2fc7c3d7197cb49d0693ac221354a0f48cfa2":
    raise SystemExit("Hari 2.3 patch checksum mismatch")

with tempfile.NamedTemporaryFile(prefix="harimt-2.3-gpu-", suffix=".patch", delete=False) as tf:
    tf.write(patch_bytes)
    patch_path = Path(tf.name)

try:
    with patch_path.open("rb") as fh:
        subprocess.run(["patch", "--dry-run", "-p1", "--batch"], cwd=root, stdin=fh, check=True)
    with patch_path.open("rb") as fh:
        subprocess.run(["patch", "-p1", "--batch"], cwd=root, stdin=fh, check=True)
finally:
    patch_path.unlink(missing_ok=True)

props_text = props.read_text(encoding="utf-8")
assert "version=2.3.0-noxviola.1" in props_text
assert "forge_mixins=[harimt.common.mixins.json,harimt.forge.mixins.json,harimt.gpu.mixins.json]" in props_text
assert '"required": true' in (root / "forge/src/main/resources/harimt.forge.mixins.json").read_text(encoding="utf-8")
assert '"required": false' in (root / "forge/src/main/resources/harimt.gpu.mixins.json").read_text(encoding="utf-8")
assert "NVIDIA_NVIDIUM" in (root / "forge/src/main/java/com/axalotl/async/forge/client/hari263/HariRenderState.java").read_text(encoding="utf-8")
assert "EXTERNAL_ALLOYIUM" in (root / "forge/src/main/java/com/axalotl/async/forge/client/hari263/HariRenderState.java").read_text(encoding="utf-8")
assert "GL43C.glDispatchCompute" in (root / "forge/src/main/java/com/axalotl/async/forge/client/hari263/compute/ComputePipeline.java").read_text(encoding="utf-8")
assert "glMultiDrawElementsIndirect" in (root / "forge/src/main/java/com/axalotl/async/forge/client/hari263/render/HariTerrainRenderer.java").read_text(encoding="utf-8")
print("Hari 2.3 GPU terrain patch applied successfully")
