#!/usr/bin/env python3
"""Fetch the exact pinned OptiFine input through its original public download page."""
import argparse
import hashlib
import http.cookiejar
import json
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

def download(spec: dict, cache: Path) -> Path:
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / spec['filename']
    if not target.is_file():
        class Links(HTMLParser):
            def __init__(self): super().__init__(); self.links = set()
            def handle_starttag(self, tag, attrs):
                if tag != 'a': return
                for key, value in attrs:
                    if key == 'href' and value:
                        url = urllib.parse.urljoin(spec['official_page'], value)
                        parsed = urllib.parse.urlparse(url)
                        if (parsed.scheme == 'https' and parsed.hostname == 'optifine.net'
                                and parsed.path == '/downloadx'
                                and urllib.parse.parse_qs(parsed.query).get('f') == [spec['official_file']]):
                            self.links.add(url)
        # Both requests belong to the same public download session. Keep the
        # same agent and official-page referrer; temporary tokens/cookies remain
        # in memory. No alternative mirror or redistribution is used.
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        headers = {'User-Agent': 'Hari-Compatibility-QA/2.4.2'}
        with opener.open(urllib.request.Request(spec['official_page'], headers=headers), timeout=120) as response:
            page = response.read().decode()
        links = Links(); links.feed(page)
        if len(links.links) != 1: raise RuntimeError('Official OptiFine page supplied no unique download')
        headers['Referer'] = spec['official_page']
        with opener.open(urllib.request.Request(next(iter(links.links)), headers=headers), timeout=180) as response:
            payload = response.read()
        if len(payload) != spec['size'] or hashlib.sha256(payload).hexdigest() != spec['hashes']['sha256']:
            raise RuntimeError('Official OptiFine input differs from pinned original bytes')
        pending = target.with_suffix('.part'); pending.write_bytes(payload); pending.replace(target)
    payload = target.read_bytes()
    if len(payload) != spec['size'] or hashlib.sha256(payload).hexdigest() != spec['hashes']['sha256']:
        raise RuntimeError('Cached OptiFine input differs from pinned original bytes')
    return target

if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--lock', type=Path, required=True); p.add_argument('--cache', type=Path, required=True)
    a = p.parse_args(); spec = json.loads(a.lock.read_text())['extra_inputs']['optifine']
    path = download(spec, a.cache)
    print(json.dumps({'input': path.name, 'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'official_only': True}))
