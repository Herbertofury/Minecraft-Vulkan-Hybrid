#!/usr/bin/env python3
"""Inspect immutable native evidence and original provider bytes; never change a game."""
import hashlib, io, json, os, pathlib, subprocess, urllib.request, urllib.error, urllib.parse, zipfile
out = pathlib.Path("provider-inspection")
out.mkdir(exist_ok=True)
def fetch(url, token=False):
    headers = {"User-Agent": "Hari-Compatibility-QA/2.4.8"}
    if token:
        headers["Authorization"] = "Bearer " + os.environ["GH_TOKEN"]
        headers["Accept"] = "application/vnd.github+json"
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, response_headers, newurl):
            return None
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=180) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        if error.code not in (301, 302, 303, 307, 308):
            raise
        destination = urllib.parse.urljoin(url, error.headers["Location"])
        # A GitHub API bearer is scoped to its origin. Signed artifact redirects
        # carry their own authorization and must receive no GitHub header.
        if urllib.parse.urlsplit(destination).hostname != urllib.parse.urlsplit(url).hostname:
            return fetch(destination, False)
        return fetch(destination, token)
artifacts = [
    (11281704693, 401508, "2be5089d50113b92fd8dc7e2a4cccdcfe475413e875f999673f2d0c21b031cd4"),
    (11281754322, 2291326, "83f123d8f65fb07d8978f312a628abfc411471451701845b7de0cfd81eb2c1c7"),
    (11281358462, 310367, "6be6151b9856b31b87dfa4b693599566230762b3322f0aecab1cfc86a67129c7"),
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
    if aid == 11281754322:
        for name in archive.namelist():
            if name.endswith("last-client-state.json") or name.endswith("harness-error.txt"):
                print("FAILED TERRAIN STATE", name, archive.read(name).decode(), flush=True)
            if name.endswith("aether-far-terrain.png"):
                import base64
                original_image = out / "aether-far-terrain-original.png"
                original_image.write_bytes(archive.read(name))
                preview = out / "aether-far-terrain-preview.png"
                subprocess.run(["convert",str(original_image),"-resize","640x360",str(preview)],check=True)
                print("NATIVE_IMAGE_BASE64",base64.b64encode(preview.read_bytes()).decode(),flush=True)
    if aid == 11281704693:
        for name in archive.namelist():
            if name.endswith("-transformed.txt"):
                source = archive.read(name).decode()
                (out / name.replace("/", "__")).write_text(source)
                if name.endswith("ThreadedLevelLightEngine-transformed.txt") or name.endswith("ChunkStatus-transformed.txt"):
                    print("ACTUAL BYTECODE",name,"\\n"+source,flush=True)
                else:
                    import re
                    parts = re.split(r"(?m)(?=^  (?:public|private|protected))", source)
                    for part in parts:
                        if "TicketType" in part or "releaseLight" in part or "Entity is already tracked" in part:
                            print("ACTUAL CHUNK TICKET BYTECODE",name,"\\n"+part,flush=True)
    if aid == 11281704693:
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
for version in ("9.6",):
    for classname in ("com.ishland.c2me.threading.lighting.mixin.MixinServerLightingProvider",
                      "com.ishland.c2me.threading.lighting.mixin.MixinThreadedAnvilChunkStorage"):
        result = subprocess.run(["javap","-classpath",str(out / receipt[version]["filename"]),"-c","-p",classname],
                                text=True,capture_output=True,check=True)
        (out / (version + "-" + classname + ".txt")).write_text(result.stdout)
        print("ORIGINAL LIGHTING BYTECODE",classname,"\\n"+result.stdout,flush=True)
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
