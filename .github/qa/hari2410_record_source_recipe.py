#!/usr/bin/env python3
"""Hash the full 2.4.10 reconstruction while preserving the immutable 2.4.9 floor."""
import argparse
import hashlib
import json
import os
from pathlib import Path

p = argparse.ArgumentParser()
for name in ('source','baseline','output'): p.add_argument('--'+name,type=Path,required=True)
a = p.parse_args()
old = json.loads(a.baseline.read_text())
excluded = ('.git/','build/','.gradle/','common/build/','forge/build/','fabric/build/','buildSrc/build/','buildSrc/.gradle/')
files = {path.relative_to(a.source).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
         for path in sorted(a.source.rglob('*')) if path.is_file()
         and not path.relative_to(a.source).as_posix().startswith(excluded)}
added = {'forge/src/main/java/net/vulkanmod/vulkan/texture/TextureUploadSpan.java',
         'forge/src/main/java/com/axalotl/async/forge/client/HariFpsCapture.java',
         'forge/src/main/java/com/axalotl/async/forge/client/HariTextureUploadProof.java'}
assert set(files) == set(old['files']) | added
changed = {name for name in old['files'] if files[name] != old['files'][name]}
assert changed == {'gradle.properties',
                   'forge/src/main/java/net/vulkanmod/vulkan/texture/VulkanImage.java',
                   'forge/src/main/java/net/vulkanmod/vulkan/memory/StagingBuffer.java'}, changed
commands = old['recipe_commands'] + [['python3','minecraft/async-1.20.1-ultimate/apply_texture_upload_span.py','upstream']]
receipt = dict(old,version='2.4.10-noxviola.1-vulkan-hybrid',product_commit=os.environ['GITHUB_SHA'],
               accepted_floor_product_commit='3bd2621048e166cf24c8a782c79edc77b8b5b2ab',
               files=files,file_count=len(files),recipe_commands=commands,
               fresh_recipe_completed=True,patched_source_differences=[],
               intentional_changes=sorted(changed | added))
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(receipt,indent=2)+'\n')
print('FRESH_COMPLETE_SOURCE_RECIPE',len(files),'files',len(commands),'commands')
