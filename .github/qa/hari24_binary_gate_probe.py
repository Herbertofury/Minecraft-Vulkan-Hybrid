#!/usr/bin/env python3
"""Evaluate the renderer selector from the exact installed production JAR."""
import argparse
import json
import subprocess
import shutil
import tempfile
from pathlib import Path

def probe(jar: Path, game: Path, java: str = "java") -> dict:
    with tempfile.TemporaryDirectory(prefix="hari-installed-gate-") as folder:
        root = Path(folder)
        (root / "Probe.java").write_text('''import net.vulkanmod.compat.UniversalRendererGate;
public class Probe { public static void main(String[] args) {
 System.out.println("RESULT=" + UniversalRendererGate.vulkanRendererEnabled());
 System.out.println("REASON=" + UniversalRendererGate.reason());
}}''')
        compiler = str(Path(java).with_name("javac")) if "/" in java else "javac"
        compiler_command = [compiler] if shutil.which(compiler) else [java, "com.sun.tools.javac.Main"]
        subprocess.run(compiler_command + ["--release", "17", "-cp", str(jar.resolve()),
                        str(root / "Probe.java")], check=True, capture_output=True, text=True)
        result = subprocess.run([java, "-cp", str(jar.resolve()) + ":" + str(root),
                                 "-Dharimt.vulkan.compatCache=false", "Probe"],
                                cwd=game.resolve(), check=True, capture_output=True, text=True)
        rows = result.stdout.splitlines()
        enabled = next(r.split("=", 1)[1] for r in rows if r.startswith("RESULT=")) == "true"
        reason = next(r.split("=", 1)[1] for r in rows if r.startswith("REASON="))
        return {"expected_renderer": "vulkan" if enabled else "opengl", "reason": reason,
                "scope": "Actual installed selector, physical mod/Jar-in-Jar scan; native Forge confirms classpath selection",
                "stdout": result.stdout, "stderr": result.stderr}

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("jar", type=Path); p.add_argument("game", type=Path)
    p.add_argument("--java", default="java"); p.add_argument("--report", type=Path)
    a = p.parse_args(); report = probe(a.jar, a.game, a.java)
    if a.report: a.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))
