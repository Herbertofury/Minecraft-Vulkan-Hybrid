#!/usr/bin/env python3
"""Install immutable official mod inputs for isolated production-client lanes."""
import argparse
import hashlib
import json
import shutil
import urllib.request
import urllib.parse
from html.parser import HTMLParser
from pathlib import Path

DIMENSIONS = ["aether", "the-midnight"]
LANES = {
    "aether": {"mods": ["aether"], "c2me": False},
    "midnight": {"mods": ["the-midnight"], "c2me": False},
    "dimensions-c2me": {"mods": DIMENSIONS, "c2me": True},
    "immediatelyfast": {"mods": DIMENSIONS + ["immediatelyfast"], "c2me": True},
    "entityculling": {"mods": DIMENSIONS + ["entityculling"], "c2me": True},
    "distanthorizons": {"mods": DIMENSIONS + ["distanthorizons"], "c2me": True},
    "oculus-embeddium": {"mods": DIMENSIONS + ["oculus", "embeddium"], "c2me": True},
    "oculus-shaders": {"mods": DIMENSIONS + ["oculus", "embeddium"], "c2me": True},
    "optifine": {"mods": DIMENSIONS, "c2me": True},
    "rubidium": {"mods": DIMENSIONS + ["rubidium"], "c2me": True},
    "lazurite": {"mods": DIMENSIONS + ["lazurite", "embeddium"], "c2me": True},
    "continuity": {"mods": DIMENSIONS + ["continuity", "lazurite", "embeddium"], "c2me": True},
    "optimized-stack": {"mods": DIMENSIONS + ["modernfix", "ferrite-core", "servercore",
                                               "entityculling", "immediatelyfast"], "c2me": True},
}
CANDIDATE_SHA = "ca2955109f188292128fec36707fc7109ba1dbacbb70d20a83659b8284ca943b"
C2ME = {"filename": "c2meforge-0.2.0-forge.9.6-all.jar", "size": 1343566,
        "url": "https://edge.forgecdn.net/files/8929/972/c2meforge-0.2.0-forge.9.6-all.jar",
        "hashes": {"sha256": "97401e625906dc7dbe7719c4915d5aa88830e64e5aa6f90831ee45a61373f8d3"}}

def verified_file(spec, cache):
    target = cache / spec["filename"]
    if not target.is_file():
        request = urllib.request.Request(spec["url"], headers={"User-Agent": "Hari-Compatibility-QA/2.4.1"})
        with urllib.request.urlopen(request, timeout=180) as response:
            payload = response.read()
        target.write_bytes(payload)
    payload = target.read_bytes()
    if len(payload) != spec["size"]:
        raise RuntimeError("Mod size mismatch: " + target.name)
    for algorithm, expected in spec["hashes"].items():
        if hashlib.new(algorithm, payload).hexdigest() != expected:
            raise RuntimeError("Mod digest mismatch: " + target.name)
    return target

def official_optifine(spec, cache):
    target = cache / spec["filename"]
    if not target.is_file():
        class Links(HTMLParser):
            def __init__(self): super().__init__(); self.links = set()
            def handle_starttag(self, tag, attrs):
                if tag == "a":
                    for key, value in attrs:
                        if key == "href" and value:
                            url = urllib.parse.urljoin(spec["official_page"], value)
                            parsed = urllib.parse.urlparse(url)
                            query = urllib.parse.parse_qs(parsed.query)
                            if parsed.scheme == "https" and parsed.hostname == "optifine.net" and parsed.path == "/downloadx" and query.get("f") == [spec["official_file"]]:
                                self.links.add(url)
        request = urllib.request.Request(spec["official_page"], headers={"User-Agent": "Hari-Compatibility-QA/2.4.2"})
        with urllib.request.urlopen(request, timeout=120) as response: page = response.read().decode()
        parser = Links(); parser.feed(page)
        if len(parser.links) != 1:
            raise RuntimeError("Official OptiFine page did not supply one unambiguous download")
        # The temporary download token stays in memory and is never logged or committed.
        with urllib.request.urlopen(next(iter(parser.links)), timeout=180) as response:
            target.write_bytes(response.read())
    payload = target.read_bytes()
    if len(payload) != spec["size"] or hashlib.sha256(payload).hexdigest() != spec["hashes"]["sha256"]:
        raise RuntimeError("Official OptiFine binary differs from pinned input")
    return target

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lane", choices=LANES, required=True)
    parser.add_argument("--lock", type=Path, required=True)
    parser.add_argument("--game", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    for folder in (args.cache, args.evidence, args.game / "mods", args.game / "config"):
        folder.mkdir(parents=True, exist_ok=True)
    lock = json.loads(args.lock.read_text())
    records = lock["records"]
    by_slug = {record["project"]["slug"]: key for key, record in records.items()}
    requested = LANES[args.lane]
    queue = [by_slug[name] for name in requested["mods"]]
    installed = {}
    while queue:
        key = queue.pop()
        if key in installed:
            continue
        record = records[key]
        if record["state"] != "resolved":
            raise RuntimeError("Unresolved required mod: " + key)
        files = record["version"]["files"]
        primary = next((f for f in files if f["primary"]), files[0])
        target = verified_file(primary, args.cache)
        shutil.copyfile(target, args.game / "mods" / target.name)
        installed[key] = {"slug": record["project"]["slug"], "version_id": record["version"]["id"],
                          "version": record["version"]["version_number"], "filename": target.name,
                          "bytes": target.stat().st_size, "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                          "dependencies": record["version"]["dependencies"]}
        for dep in record["version"]["dependencies"]:
            if dep["dependency_type"] == "required":
                if dep["project_id"] not in records:
                    raise RuntimeError("Missing required dependency record: " + dep["project_id"])
                queue.append(dep["project_id"])
    candidate = args.candidate.read_bytes()
    if hashlib.sha256(candidate).hexdigest() != CANDIDATE_SHA:
        raise RuntimeError("Candidate differs from the pinned candidate artifact")
    shutil.copyfile(args.candidate, args.game / "mods" / "HariMultiThread-Ultimate.jar")
    if requested["c2me"]:
        target = verified_file(C2ME, args.cache)
        shutil.copyfile(target, args.game / "mods" / target.name)
        (args.game / "config" / "c2me.toml").write_text("version = 3\n[fixes]\nenforceSafeWorldRandomAccess = true\n")
    if args.lane == "optifine":
        target = official_optifine(lock["extra_inputs"]["optifine"], args.cache)
        shutil.copyfile(target, args.game / "mods" / target.name)
        installed["optifine"] = {"filename": target.name, "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "official_only": True}
    if args.lane == "oculus-shaders":
        spec = lock["extra_inputs"]["makeup-ultra-fast"]["files"][0]
        target = verified_file(spec, args.cache)
        (args.game / "shaderpacks").mkdir(exist_ok=True)
        shutil.copyfile(target, args.game / "shaderpacks" / target.name)
        (args.game / "config/oculus.properties").write_text("enableShaders=true\nshaderPack=" + target.name + "\n")
        installed["shaderpack"] = {"filename": target.name, "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "unmodified_default_pack": True}
    (args.game / "config" / "harimt.toml").write_text('["Async Config"]\nparaMax = 2\n')
    receipt = {"lane": args.lane, "candidate_sha256": CANDIDATE_SHA, "c2me": requested["c2me"],
               "installed": installed, "third_party_mods_unchanged": True}
    (args.evidence / "installed-mod-inputs.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps({"lane": args.lane, "installed_mods": len(installed), "c2me": requested["c2me"]}))

if __name__ == "__main__":
    main()
