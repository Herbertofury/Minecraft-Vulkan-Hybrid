#!/usr/bin/env python3
"""Focused provider update lane; immutable product and baseline acceptance stay unchanged."""
import hashlib, json, sys
from pathlib import Path
import hari24_modpack_inputs as base
spec = json.loads(Path(__file__).with_name("hari24-c2me-9.8-input.json").read_text())
base.C2ME = spec["file"]
base.main()
game = Path(sys.argv[sys.argv.index("--game") + 1])
evidence = Path(sys.argv[sys.argv.index("--evidence") + 1])
path = game / "mods" / base.C2ME["filename"]
assert path.stat().st_size == base.C2ME["size"]
assert hashlib.sha256(path.read_bytes()).hexdigest() == base.C2ME["hashes"]["sha256"]
receipt_path = evidence / "installed-mod-inputs.json"
receipt = json.loads(receipt_path.read_text())
receipt["c2me_input"] = spec
receipt_path.write_text(json.dumps(receipt, indent=2) + "\n")
