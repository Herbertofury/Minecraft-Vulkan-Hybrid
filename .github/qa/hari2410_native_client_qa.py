#!/usr/bin/env python3
"""Full original native actor plus exact GPU pixel proof; no validation weakened."""
from pathlib import Path
actor = Path(__file__).with_name('hari249_current_validation_qa.py')
source = actor.read_text()
old = '"--jvm-arg=-Dharimt.qa.captureFrames=true",'
assert source.count(old) == 1
source = source.replace(old, '"--jvm-arg=-Dharimt.qa.verifyTextureUploads=true",\n            ' + old)
old = '        shutil.copyfile(frame_report, evidence / "frame-sample.json")'
assert source.count(old) == 1
source = source.replace(old, old + '''
        if args.expect == "vulkan":
            wait_for("[Hari/QA] exact Vulkan texture readback passed")
            import hashlib
            proof = mc_dir / "harimt-texture-upload-proof.json"
            result = json.loads(proof.read_text())
            if not result.get("passed") or len(result["results"]) != 6:
                raise RuntimeError("Exact Vulkan texture readback proof incomplete")
            shutil.copyfile(proof, evidence / "texture-upload-proof.json")
''')
exec(compile(source, str(actor), 'exec'), {'__name__':'__main__','__file__':str(actor)})
