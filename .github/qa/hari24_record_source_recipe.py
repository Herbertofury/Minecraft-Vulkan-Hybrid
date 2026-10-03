#!/usr/bin/env python3
"""Record every freshly reconstructed source file before generated build outputs exist."""
import argparse, hashlib, json, os
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("--source",type=Path,required=True)
p.add_argument("--baseline",type=Path,required=True)
p.add_argument("--output",type=Path,required=True)
a=p.parse_args()
old=json.loads(a.baseline.read_text())
excluded=(".git/","build/",".gradle/","common/build/","forge/build/","fabric/build/","buildSrc/build/","buildSrc/.gradle/")
files={path.relative_to(a.source).as_posix():hashlib.sha256(path.read_bytes()).hexdigest()
       for path in sorted(a.source.rglob("*")) if path.is_file()
       and not path.relative_to(a.source).as_posix().startswith(excluded)}
assert set(files)==set(old["files"]), {"new":sorted(set(files)-set(old["files"])),
    "missing":sorted(set(old["files"])-set(files))}
receipt=dict(old,version="2.4.9-noxviola.1-vulkan-hybrid",product_commit=os.environ["GITHUB_SHA"],
    files=files,file_count=len(files),fresh_recipe_completed=True,patched_source_differences=[])
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(receipt,indent=2)+"\n")
print("FRESH_COMPLETE_SOURCE_RECIPE",len(files),"files",len(receipt["recipe_commands"]),"commands")
