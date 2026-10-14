#!/usr/bin/env python3
"""Package the exact reconstructed source and executable pinned recipe, without runtime dumps."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    for name in ("source", "repo", "receipt", "candidate", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    receipt = json.loads(args.receipt.read_text())
    candidate = json.loads(args.candidate.read_text())
    if (not receipt["fresh_recipe_completed"] or receipt["patched_source_differences"]
            or receipt["product_commit"] != candidate["product_commit"]
            or receipt["version"] != candidate["version"] or not candidate["reproducible"]):
        raise RuntimeError("Source and byte-reproducible candidate lineage do not agree")
    entries = {}
    for relative, expected in receipt["files"].items():
        data = (args.source / relative).read_bytes()
        if digest(data) != expected:
            raise RuntimeError("Reconstructed source changed: " + relative)
        entries["source/" + relative] = data
    if len(receipt["files"]) != receipt["file_count"]:
        raise RuntimeError("Incomplete source receipt")
    project = args.repo / "minecraft/async-1.20.1-ultimate"
    scripts = {Path(command[1]).name for command in receipt["recipe_commands"]}
    scripts.update(("apply_swapchain_sync.py", "apply_modpack_render_integrity.py",
                    "apply_vulkan_shader_integrity.py", "build_gl_contract.py"))
    for name in sorted(scripts):
        entries["reproduce/" + name] = (project / name).read_bytes()
    for folder in ("overlay", "backend-overlay", "mc263_gpu_terrain_patch",
                   "persistent_gpu_scene_patch", "vulkan-hybrid-src", "performance-src"):
        for path in sorted((project / folder).rglob("*")):
            if path.is_file():
                entries["reproduce/" + path.relative_to(project).as_posix()] = path.read_bytes()
    for path in sorted(project.glob("test_*.py")):
        entries["reproduce/" + path.name] = path.read_bytes()
    entries["ci/compile.yml"] = (args.repo / ".github/workflows/async-1.20.1-ultimate-2.4.0-vulkan-hybrid.yml").read_bytes()
    entries["SOURCE-RECIPE.json"] = args.receipt.read_bytes()
    entries["CANDIDATE.json"] = args.candidate.read_bytes()
    version = candidate["version"].split("-", 1)[0]
    entries["BUILD.md"] = (
        f"# HariMultiThread Ultimate {version} Vulkan Hybrid source\n\n"
        "Minecraft 1.20.1, Forge 47.4.23, Java 17.\n\n"
        "`source/` contains the complete merged Gradle project. On Linux/macOS run:\n\n"
        "```sh\ncd source\nchmod +x gradlew\n./gradlew --no-daemon clean :forge:build --stacktrace\n```\n\n"
        "On Windows use `gradlew.bat --no-daemon clean :forge:build --stacktrace`.\n\n"
        "The source was freshly reconstructed from the pinned upstream commits with all 23 recipe steps. "
        "SOURCE-RECIPE.json hashes every source file. CANDIDATE.json identifies the exact independently "
        "reproduced JAR and its compile run. Native compatibility acceptance is provided separately.\n\n"
        "`reproduce/` retains every recipe script, helper, overlay and focused regression; "
        "`ci/compile.yml` records their order and actual build gates. Upstream licenses are retained "
        "inside source/. This archive contains no user worlds, runtime dumps or third-party mod binaries.\n"
    ).encode()
    entries["SHA256SUMS.txt"] = "".join(digest(data) + "  " + name + "\n"
                                        for name, data in sorted(entries.items())).encode()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if name.endswith("/gradlew") else 0o100644) << 16
            archive.writestr(info, data, compresslevel=9)
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Archive CRC check failed")
        for line in archive.read("SHA256SUMS.txt").decode().splitlines():
            expected, name = line.split("  ", 1)
            if digest(archive.read(name)) != expected:
                raise RuntimeError("Archive hash check failed: " + name)
    print(json.dumps({"file": str(args.output.resolve()), "bytes": args.output.stat().st_size,
                      "sha256": digest(args.output.read_bytes()), "source_files": receipt["file_count"],
                      "recipe_steps": len(receipt["recipe_commands"]), "runtime_dumps_included": False}, indent=2))


if __name__ == "__main__":
    main()
