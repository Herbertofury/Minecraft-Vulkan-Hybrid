#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_ci_repository_hygiene.py <merged-upstream-root>')
root = Path(sys.argv[1]).resolve()
files = [
    root / 'common/build.gradle',
    root / 'forge/build.gradle',
    root / 'fabric/build.gradle',
    root / 'buildSrc/src/main/groovy/multiloader-common.gradle',
]
patterns = {
    "    maven { name = 'Bawnorton'; url = 'https://maven.bawnorton.com/releases' }\n": 3,
    "    maven { url = 'https://maven.bawnorton.com/releases' }\n": 1,
}
combined = ''.join(p.read_text(encoding='utf-8') for p in files)
for pat, expected in patterns.items():
    actual = combined.count(pat)
    if actual != expected:
        raise SystemExit(f'Bawnorton repository shape drift: expected {expected}, found {actual}: {pat.strip()}')
for p in files:
    for lineno, line in enumerate(p.read_text(encoding='utf-8').splitlines(), 1):
        stripped = line.strip()
        if 'com.github.bawnorton' in stripped and not stripped.startswith('//'):
            raise SystemExit(f'active Bawnorton dependency at {p}:{lineno}: {stripped}')
for p in files:
    text = p.read_text(encoding='utf-8')
    for pat in patterns:
        text = text.replace(pat, '')
    p.write_text(text, encoding='utf-8')
remaining = ''.join(p.read_text(encoding='utf-8') for p in files)
if 'maven.bawnorton.com' in remaining:
    raise SystemExit('Bawnorton repository URL survived repository hygiene')
print('CI repository hygiene applied: removed 4 unused Bawnorton repository entries')
