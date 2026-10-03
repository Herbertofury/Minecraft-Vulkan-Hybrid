#!/usr/bin/env python3
"""Inspect immutable native evidence and original provider bytes; never change a game."""
import hashlib, io, json, os, pathlib, subprocess, urllib.request, zipfile
out = pathlib.Path("provider-inspection")
out.mkdir(exist_ok=True)
def fetch(url, token=False):
    headers = {"User-Agent": "Hari-Compatibility-QA/2.4.8"}
    if token:
        headers["Authorization"] = "Bearer " + os.environ["GH_TOKEN"]
        headers["Accept"] = "application/vnd.github+json"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=180) as response:
        return response.read()
artifacts = [
    (11281642663, 332680, "3136b7ff9241ac9177602ce72805eae946753ff63eeded135eb4903c5dd74116"),
    (11281697663, 13494730, "632e128a736edbf9137241957d9df59229791a8fca4aa63f131f1b91ea5de4ff"),
]
for aid, size, digest in artifacts:
    data = fetch(f"https://api.github.com/repos/Herbertofury/ProjectDump/actions/artifacts/{aid}/zip", True)
    assert len(data) == size, (aid, len(data), size)
    assert hashlib.sha256(data).hexdigest() == digest, aid
    archive = zipfile.ZipFile(io.BytesIO(data))
    assert archive.testzip() is None, aid
    print(f"ARTIFACT VERIFIED {aid} {size} {digest}", flush=True)
    for name in archive.namelist():
        if "runtime-snapshot" in name and name.endswith(".json") or name.endswith("production-client-proof.json"):
            payload = archive.read(name)
            target = out / (str(aid) + "-" + name.replace("/", "__"))
            target.write_bytes(payload)
            print("EVIDENCE", aid, name, payload.decode(), flush=True)
    if aid == 11281642663:
        for name in archive.namelist():
            if name.endswith("latest.log"):
                lines = archive.read(name).decode(errors="replace").splitlines()
                print("SHUTDOWN LOG", name, "\n".join(lines[-100:]), flush=True)
providers = [
    ("9.6", "8929/972", "97401e625906dc7dbe7719c4915d5aa88830e64e5aa6f90831ee45a61373f8d3"),
    ("9.8", "9040/801", None),
]
jars = {}
receipt = {}
for version, part, expected in providers:
    name = f"c2meforge-0.2.0-forge.{version}-all.jar"
    url = f"https://edge.forgecdn.net/files/{part}/{name}"
    data = fetch(url)
    digest = hashlib.sha256(data).hexdigest()
    if expected:
        assert digest == expected
    archive = zipfile.ZipFile(io.BytesIO(data))
    assert archive.testzip() is None
    path = out / name
    path.write_bytes(data)
    jars[version] = archive
    receipt[version] = {"filename": name, "url": url, "size": len(data), "hashes": {"sha256": digest}}
    print("ORIGINAL PROVIDER", json.dumps(receipt[version]), flush=True)
    print("PROVIDER MANIFEST", version, archive.read("META-INF/mods.toml").decode(), flush=True)
changed = [name for name in jars["9.8"].namelist() if name.endswith(".class") and
           (name not in jars["9.6"].namelist() or jars["9.8"].read(name) != jars["9.6"].read(name))]
receipt["changed_classes"] = changed
print("CHANGED CLASSES", json.dumps(changed, indent=2), flush=True)
for name in changed:
    if any(term in name.lower() for term in ("chunkstorage", "chunkserializer", "lightingprovider", "minecraftserver", "entitystorage", "chunkticket", "reentrant", "chunkholder", "notick", "chunkio")):
        for version in ("9.6", "9.8"):
            if name not in jars[version].namelist():
                continue
            classname = name.removesuffix(".class").replace("/", ".")
            result = subprocess.run(["javap", "-classpath", str(out / receipt[version]["filename"]), "-c", "-p", classname],
                                    text=True, capture_output=True, check=True)
            (out / (version + "-" + name.replace("/", "__") + ".txt")).write_text(result.stdout)
(out / "original-provider-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
# Evidence and bytecode only are distributable QA output here. Original provider
# JARs are consumed by this inspection and omitted from its artifact.
for path in out.glob("*.jar"):
    path.unlink()
