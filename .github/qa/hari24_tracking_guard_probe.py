#!/usr/bin/env python3
"""Verify the actual packaged tracking mixin retains original wrappers and no exception cancellation."""
import argparse, hashlib, json, subprocess, zipfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("--jar", type=Path, required=True)
p.add_argument("--upstream", type=Path, required=True)
p.add_argument("--report", type=Path, required=True)
a=p.parse_args()
name="com/axalotl/async/common/mixin/server/ChunkMapMixin.class"
with zipfile.ZipFile(a.jar) as z:
    compiled=z.read(name)
baseline=(a.upstream/"common/bin/main"/name).read_bytes()
assert b"skipThrowLoadEntity" in baseline, "Pinned original cancellation negative control drifted"
assert b"skipThrowLoadEntity" not in compiled, "Packaged production retains original tracking error cancellation"
assert b"pauseInIde" not in compiled and b"ci.cancel" not in compiled
result=subprocess.run(["javap","-classpath",str(a.jar),"-c","-p",
    "com.axalotl.async.common.mixin.server.ChunkMapMixin"],text=True,capture_output=True,check=True)
text=result.stdout
for method in ("addEntity", "removeEntity"):
    start=text.index("private synchronized void "+method+"(")
    end=text.index("\n  }",start) if "\n  }" in text[start:] else len(text)
    # Each wrapper invokes the exact supplied original operation, with no
    # handler table or cancellation return introduced.
    body=text[start:text.find("\n  private",start+1) if "\n  private" in text[start+1:] else len(text)]
    assert "Operation.call" in body, method+" dropped original method"
    assert "Exception table:" not in body, method+" intercepts original failure"
a.report.parent.mkdir(parents=True,exist_ok=True)
a.report.with_suffix(".javap.txt").write_text(text)
a.report.write_text(json.dumps({"packaged_mixin_sha256":hashlib.sha256(compiled).hexdigest(),
    "original_pinned_cancellation_negative_control_sha256":hashlib.sha256(baseline).hexdigest(),
    "packaged_cancel_injector_absent":True,"original_add_remove_wrappers_retained":True,
    "original_add_remove_monitors_retained":True,"throw_interception_absent":True,
    "native_transformed_guard_acceptance_required":True},indent=2)+"\n")
print("PACKAGED_TRACKING_ORIGINAL_FAILURE_RETAINED_PASS")
