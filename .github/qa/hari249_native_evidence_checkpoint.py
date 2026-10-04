#!/usr/bin/env python3
"""Inspect immutable native evidence and original provider bytes; never change a game."""
import hashlib, io, json, os, pathlib, subprocess, urllib.request, urllib.error, urllib.parse, zipfile
out = pathlib.Path("native-checkpoint")
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

REPOSITORY = "Herbertofury/ProjectDump"
HEAD = "3bd2621048e166cf24c8a782c79edc77b8b5b2ab"
JAR_SHA = "c1082c8282ff40221c0ea982ce329ec6ab9a26c80790d643d1277b412b8fb396"
INPUTS = [
 (37163922589, 11288359841, 30682369, "8638d5275ffdeb62c0f824d077dd6b4252aee678d234f01d93d14bac8c2b6115", "dedicated"),
 (37163922551, 11288592435, 50473901, "f6a05c2121acf446af3de02bfd50ba9f8bb869c4fa6c5922368f3d3eeec62d19", "production"),
]
receipts = []
for run_id, aid, size, expected_digest, label in INPUTS:
    run = json.loads(fetch(f"https://api.github.com/repos/{REPOSITORY}/actions/runs/{run_id}", True))
    assert run["head_sha"] == HEAD and run["status"] == "completed" and run["conclusion"] == "success"
    metadata = json.loads(fetch(f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{aid}", True))
    assert metadata["workflow_run"]["id"] == run_id
    assert metadata["size_in_bytes"] == size and metadata["digest"] == "sha256:" + expected_digest
    data = fetch(f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{aid}/zip", True)
    assert len(data) == size and hashlib.sha256(data).hexdigest() == expected_digest
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        assert archive.testzip() is None
        own_jars = []
        kept = {}
        for info in archive.infolist():
            if info.is_dir():
                continue
            relative = pathlib.PurePosixPath(info.filename)
            assert not relative.is_absolute() and ".." not in relative.parts
            payload = archive.read(info)
            sha = hashlib.sha256(payload).hexdigest()
            if relative.suffix == ".jar":
                assert sha == JAR_SHA, ("Unexpected binary in exact native evidence", info.filename, sha)
                own_jars.append({"path": info.filename, "bytes": len(payload), "sha256": sha})
                continue
            target = (out / label / str(relative)).resolve()
            assert target.is_relative_to(out.resolve())
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
            kept[info.filename] = sha
        assert len(own_jars) == 1, (label, own_jars)
        receipts.append({"run": run_id, "artifact": aid, "artifact_bytes": size,
                         "artifact_sha256": expected_digest, "head": HEAD, "conclusion": "success",
                         "verified_own_jar_saved_separately": own_jars, "evidence_files": kept})
receipt = {"status": "Exact standard native PASS; complete modpack acceptance pending",
           "jar_sha256": JAR_SHA, "runs": receipts,
           "omissions": "Only the independently verified duplicate own JAR; all native logs, captures and proofs retained."}
(out / "NATIVE-CHECKPOINT-RECEIPT.json").write_text(json.dumps(receipt, indent=2) + "\n")
print("NATIVE_CHECKPOINT_VERIFIED", HEAD, JAR_SHA, [(r["run"],len(r["evidence_files"])) for r in receipts])
