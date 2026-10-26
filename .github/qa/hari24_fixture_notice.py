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
LAZURITE_WARNING_MASKS = {
    "960x72+160+12": "403dc1c32ef9ca110847315c45b54611ee3e04b0dce3450387b364b497a4d68d",
    "1256x44+12+110": "cc4bac56b145d8a16c7efb454c15dfc48265a813ad813218d391ce7c424573c6",
    "550x48+365+650": "c19815f31f339a3a8e9f2f7f04cd43a28650411e7039fcb1c6b3b2f4d22d6c91",
}
TITLE_BUTTON_MASKS = {
    "240x30+520+339": "28c30278c5dab138924debd1fcb07d9758dafe9e832473809846247398b49463",
    "240x30+520+411": "8f6d25cea006275785081850b4e51268ae6f9f5a84dbe13938e7fb69fdc090b3",
    "270x30+350+591": "14288cc5205b4ea6487cd734ef90f6109a7c9f806fc0d83bccc41600c0895698",
}

def matches_masks(image: Path, env: dict, masks: dict) -> bool:
    size = subprocess.check_output(["identify", "-format", "%w %h", str(image)],
                                   env=env, text=True, timeout=10).strip()
    if size != "1280 720":
        return False
    for region, expected in masks.items():
        pixels = subprocess.check_output([
            "convert", str(image), "-crop", region, "+repage", "-colorspace", "Gray",
            "-threshold", "78.431372549%", "-depth", "8", "gray:-"], env=env, timeout=10)
        if hashlib.sha256(pixels).hexdigest() != expected:
            return False
    return True

def matches_notice(image: Path, env: dict) -> bool:
    return matches_masks(image, env, MASKS)

class FixtureNotice:
    def __init__(self, world: str, evidence: Path, env: dict, game: Path):
        self.enabled = world == "HMT-2.4-QA"
        self.evidence, self.env = evidence, env
        self.game = game
        self.last_check, self.handled = 0.0, set()

    def poll(self):
        if not self.enabled or time.monotonic() - self.last_check < 5:
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
        state_file = self.game / "harimt-qa-client-state.json"
        state = json.loads(state_file.read_text()) if state_file.is_file() else {}
        fresh = time.time() * 1000 - state.get("observed_at_epoch_ms", 0) < 5000
        if "lazurite_warning" in self.handled and fresh:
            if ("singleplayer" not in self.handled and state.get("dimension") is None
                    and matches_masks(shot, self.env, TITLE_BUTTON_MASKS)):
                shot.rename(self.evidence / "lazurite-native-main-menu.png")
                subprocess.run(["xdotool", "windowactivate", "--sync", wid,
                                "mousemove", "--window", wid, "640", "354", "click", "1"],
                               env=self.env, check=True, timeout=20)
                self.handled.add("singleplayer")
                return
            if ("singleplayer" in self.handled and "fixture_selected" not in self.handled
                    and state.get("screen") == "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen"):
                worlds = sorted(p.name for p in (self.game / "saves").iterdir()
                                if p.is_dir() and (p / "level.dat").is_file())
                if worlds != ["HMT-2.4-QA"]:
                    raise RuntimeError("Native QA world selection is ambiguous: " + repr(worlds))
                shot.rename(self.evidence / "lazurite-native-world-selection.png")
                # Vanilla 1.20.1 SelectWorldScreen: search at GUI y=22..42;
                # first list row y=52..88. The observed 1280x720 menu uses scale 3.
                subprocess.run(["xdotool", "windowactivate", "--sync", wid,
                                "mousemove", "--window", wid, "640", "96", "click", "1",
                                "type", "--clearmodifiers", "--delay", "30", "HMT-2.4-QA"],
                               env=self.env, check=True, timeout=20)
                time.sleep(1)
                subprocess.run(["import", "-display", self.env["DISPLAY"], "-window", wid,
                                str(self.evidence / "lazurite-filtered-qa-world.png")],
                               env=self.env, check=True, timeout=20)
                subprocess.run(["xdotool", "mousemove", "--window", wid, "640", "194",
                                "click", "--repeat", "2", "--delay", "100", "1"],
                               env=self.env, check=True, timeout=20)
                self.handled.add("fixture_selected")
                (self.evidence / "lazurite-world-selection-action.json").write_text(json.dumps({
                    "world": "HMT-2.4-QA", "only_available_world": worlds,
                    "actual_screen_class": state["screen"], "matched_original_main_menu_text": True,
                    "action": "Native Singleplayer, exact QA world search and first filtered result double click",
                    "mods_removed_or_settings_changed": False,
                }, indent=2) + "\n")
                return
        if "lazurite_warning" not in self.handled and matches_masks(shot, self.env, LAZURITE_WARNING_MASKS):
            before = self.evidence / "lazurite-embeddium-warning.png"
            shot.rename(before)
            subprocess.run(["xdotool", "windowactivate", "--sync", wid,
                            "mousemove", "--window", wid, "640", "677", "click", "1"],
                           env=self.env, check=True, timeout=20)
            self.handled.add("lazurite_warning")
            (self.evidence / "lazurite-warning-action.json").write_text(json.dumps({
                "world": "HMT-2.4-QA", "matched_all_original_text_masks": True,
                "message": "Embeddium includes FRAPI support as of 0.3.20. Lazurite should be removed",
                "action": "Native Proceed to Main Menu to test the requested original compatibility stack",
                "before_screenshot": before.name, "mods_removed_or_settings_changed": False,
            }, indent=2) + "\n")
            return
        if "experimental_world" in self.handled:
            return
        if not matches_notice(shot, self.env):
            return
        before = self.evidence / "experimental-world-notice.png"
        shot.rename(before)
        subprocess.run(["xdotool", "windowactivate", "--sync", wid,
                        "mousemove", "--window", wid, "400", "477", "click", "1"],
                       env=self.env, check=True, timeout=20)
        self.handled.add("experimental_world")
        (self.evidence / "fixture-notice-action.json").write_text(json.dumps({
            "world": "HMT-2.4-QA", "matched_all_original_text_masks": True,
            "action": "Native click on Proceed in disposable fixture",
            "before_screenshot": before.name, "product_or_mod_settings_changed": False,
        }, indent=2) + "\n")
