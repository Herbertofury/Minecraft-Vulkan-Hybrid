#!/usr/bin/env python3
"""Click only the experimentally flagged, disposable Minecraft QA world's notice."""
import hashlib
import json
import subprocess
import time
from pathlib import Path

# White text masks from the actual unchanged 1.20.1 native screen in artifact
# 11265350768 (ZIP SHA256 2fd155847d0ab58bba2b51c4053154b59ef9a593d7f040e0f87bf6bdc12a05fc).
# All three must match: experimental-world title, Proceed, and Cancel. Unknown
# dialogs are left visible and cause the ordinary join timeout to fail.
MASKS = {
    "840x32+220+212": "59ce2971036cdc177454031012e2240aa872800183f03268c7403c04b77af3dd",
    "210x37+300+458": "7542380343dcab2d664d6828de89ff6c607d4cc8d767977749a024a8dc5080dc",
    "215x37+780+458": "2f88f28970f55c374695d8b67443f0762db792a001828b93e801dda0400701b0",
}

def matches_notice(image: Path, env: dict) -> bool:
    size = subprocess.check_output(["identify", "-format", "%w %h", str(image)],
                                   env=env, text=True, timeout=10).strip()
    if size != "1280 720":
        return False
    for region, expected in MASKS.items():
        pixels = subprocess.check_output([
            "convert", str(image), "-crop", region, "+repage", "-colorspace", "Gray",
            "-threshold", "78.431372549%", "-depth", "8", "gray:-"], env=env, timeout=10)
        if hashlib.sha256(pixels).hexdigest() != expected:
            return False
    return True

class FixtureNotice:
    def __init__(self, world: str, evidence: Path, env: dict):
        self.enabled = world == "HMT-2.4-QA"
        self.evidence, self.env = evidence, env
        self.last_check, self.clicked = 0.0, False

    def poll(self):
        if not self.enabled or self.clicked or time.monotonic() - self.last_check < 5:
            return
        self.last_check = time.monotonic()
        windows = subprocess.run(["xdotool", "search", "--onlyvisible", "--name", "Minecraft"],
                                 env=self.env, capture_output=True, text=True, timeout=10)
        if not windows.stdout.strip():
            return
        wid = windows.stdout.splitlines()[-1]
        shot = self.evidence / "world-entry-current.png"
        subprocess.run(["import", "-display", self.env["DISPLAY"], "-window", wid, str(shot)],
                       env=self.env, check=True, timeout=20)
        if not matches_notice(shot, self.env):
            return
        before = self.evidence / "experimental-world-notice.png"
        shot.rename(before)
        subprocess.run(["xdotool", "windowactivate", "--sync", wid,
                        "mousemove", "--window", wid, "400", "477", "click", "1"],
                       env=self.env, check=True, timeout=20)
        self.clicked = True
        (self.evidence / "fixture-notice-action.json").write_text(json.dumps({
            "world": "HMT-2.4-QA", "matched_all_original_text_masks": True,
            "action": "Native click on Proceed in disposable fixture",
            "before_screenshot": before.name, "product_or_mod_settings_changed": False,
        }, indent=2) + "\n")
