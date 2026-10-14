#!/usr/bin/env python3
"""Resolve official Forge 1.20.1 builds and preserve every required dependency.

The manifest pins version IDs, complete provider metadata and SHA-512/size checks.
No third-party mod is patched, repackaged, stripped, or included in Hari releases.
"""
from __future__ import annotations
import argparse
import concurrent.futures
import hashlib
import json
import urllib.parse
import urllib.request
import urllib.error
from pathlib import Path

PINS = {
    "aether": "2411kUqF", "the-midnight": "mPTFUoKg",
    "immediatelyfast": "rvsLEEZU", "entityculling": "HPDH6g5B",
    "distanthorizons": "6UnEfsRQ", "lazurite": "3Ro7216v",
    "oculus": "iQ1SwGc3", "embeddium": "UTbfe5d1",
}
HEADERS = {"User-Agent": "HariMultiThread-Forge-Compatibility-QA/2.4.1"}
CACHE: Path | None = None

def fetch_json(endpoint: str):
    target = CACHE / (hashlib.sha256(endpoint.encode()).hexdigest() + ".json") if CACHE else None
    if target and target.is_file():
        return json.loads(target.read_text())["response"]
    request = urllib.request.Request("https://api.modrinth.com/v2/" + endpoint, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=90) as response:
        payload = json.load(response)
    if target:
        target.write_text(json.dumps({"endpoint": endpoint, "response": payload}, indent=2) + "\n")
    return payload

def resolve(project: str, version: str | None = None):
    meta = fetch_json("project/" + project)
    if version:
        selected = fetch_json("version/" + version)
    else:
        query = urllib.parse.urlencode({"game_versions": '["1.20.1"]', "loaders": '["forge"]'})
        candidates = fetch_json("project/" + project + "/version?" + query)
        if not candidates:
            return {"project": meta, "state": "no_published_forge_1.20.1_version", "versions": []}
        releases = [v for v in candidates if v["version_type"] == "release"]
        selected = (releases or candidates)[0]
    if "1.20.1" not in selected["game_versions"] or "forge" not in selected["loaders"]:
        raise RuntimeError("Incorrect target version/loader: " + selected["id"])
    return {"project": meta, "state": "resolved", "version": selected}

def main():
    global CACHE
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--projects", nargs="+", default=list(PINS))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    CACHE = args.output / "provider-metadata"
    CACHE.mkdir(exist_ok=True)
    records = {}
    queue = [(name, PINS.get(name)) for name in args.projects]
    while queue:
        def resolve_preserving_failure(item):
            try:
                return resolve(*item)
            except Exception as failure:
                return {"project": {"id": item[0], "slug": item[0]},
                        "state": "unresolved_provider_lookup", "error": repr(failure)}
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            resolved = list(pool.map(resolve_preserving_failure, queue))
        queue = []
        for record in resolved:
            project_id = record["project"]["id"]
            if project_id in records:
                continue
            records[project_id] = record
            if record["state"] != "resolved":
                continue
            for dep in record["version"]["dependencies"]:
                if dep["dependency_type"] == "required" and dep["project_id"] not in records:
                    queue.append((dep["project_id"], dep["version_id"]))
        # Deduplicate by actual project identity. Preserve a required version pin.
        queue = list(dict.fromkeys(queue))
    def download(record):
        if record["state"] != "resolved":
            return
        files = record["version"]["files"]
        primary = next((f for f in files if f["primary"]), files[0])
        target = args.output / primary["filename"]
        if not target.is_file():
            request = urllib.request.Request(primary["url"], headers=HEADERS)
            with urllib.request.urlopen(request, timeout=180) as response:
                payload = response.read()
            target.write_bytes(payload)
        payload = target.read_bytes()
        if len(payload) != primary["size"]:
            raise RuntimeError("Provider size mismatch: " + target.name)
        for algorithm, expected in primary["hashes"].items():
            if hashlib.new(algorithm, payload).hexdigest() != expected:
                raise RuntimeError("Provider digest mismatch: " + target.name)
        record["verified_file"] = {"filename": target.name, "bytes": len(payload),
                                   "sha256": hashlib.sha256(payload).hexdigest()}
    def download_preserving_failure(record):
        try:
            download(record)
        except Exception as failure:
            record["state"] = "unresolved_download_or_integrity"
            record["error"] = repr(failure)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(download_preserving_failure, records.values()))
    manifest = {"schema": 1, "minecraft": "1.20.1", "loader": "forge",
                "requested": args.projects, "records": records}
    (args.output / "MOD-INPUTS.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for record in records.values():
        print(json.dumps({"project": record["project"]["slug"], "state": record["state"],
                          "version": record.get("version", {}).get("version_number"),
                          "verified_file": record.get("verified_file")}))

if __name__ == "__main__":
    main()
