#!/usr/bin/env python3
"""Inspect immutable native evidence and original provider bytes; never change a game."""
import hashlib, io, json, os, pathlib, subprocess, urllib.request, urllib.error, urllib.parse, zipfile
out = pathlib.Path("compiled-checkpoint")
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

import time
REPOSITORY = "Herbertofury/ProjectDump"
RUN = 37163922566
HEAD = "3bd2621048e166cf24c8a782c79edc77b8b5b2ab"
deadline = time.monotonic() + 600
while True:
    run = json.loads(fetch(f"https://api.github.com/repos/{REPOSITORY}/actions/runs/{RUN}", True))
    assert run["head_sha"] == HEAD
    if run["status"] == "completed":
        assert run["conclusion"] == "success", run["conclusion"]
        break
    assert time.monotonic() < deadline, "Exact compile did not complete within ten minutes"
    time.sleep(15)
available = json.loads(fetch(f"https://api.github.com/repos/{REPOSITORY}/actions/runs/{RUN}/artifacts", True))
artifacts = [a for a in available["artifacts"] if a["name"] == "HariMultiThread-Ultimate-1.20.1-2.4.0-VULKAN-HYBRID-COMPILE"]
assert len(artifacts) == 1
artifact = artifacts[0]
data = fetch(f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact['id']}/zip", True)
assert len(data) == artifact["size_in_bytes"]
assert "sha256:" + hashlib.sha256(data).hexdigest() == artifact["digest"]
archive = zipfile.ZipFile(io.BytesIO(data))
assert archive.testzip() is None
JAR = "HariMultiThread-Ultimate-1.20.1-2.4.9-vulkan-hybrid.jar"
jar_data = archive.read(JAR)
jar_sha = hashlib.sha256(jar_data).hexdigest()
assert hashlib.sha256(archive.read("REPRO-SECOND.jar")).hexdigest() == jar_sha
recipe = json.loads(archive.read("SOURCE-RECIPE-2.4.9.json"))
assert recipe["product_commit"] == HEAD and recipe["file_count"] == 998 and len(recipe["files"]) == 998
assert len(recipe["recipe_commands"]) == 23 and recipe["fresh_recipe_completed"] and not recipe["patched_source_differences"]
light = json.loads(archive.read("C2ME-LIGHT-LEVELS.json"))
assert light["passed"] and light["actual_postapply_plugin_after_provider_merge"] and light["early_plugin_before_provider_merge_negative_control"]
assert light["srg_renamed_release_operations"] and light["original_owner_thread_removal_redirect_retained"] and light["existing_C2ME_removal_level_helper_retained"]
for name in archive.namelist():
    if name.endswith(".jar") and name != JAR:
        continue
    target = (out / name).resolve()
    assert target.is_relative_to(out.resolve())
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(archive.read(name))
javap = subprocess.check_output(["javap", "-classpath", str(out / JAR), "-c", "-p",
    "com.axalotl.async.forge.mixin.HariForgeMixinPlugin"], text=True)
(out / "COMPILED-PLUGIN-PHASE.txt").write_text(javap)
pre = javap.split("public void preApply(", 1)[1].split("public void postApply(", 1)[0]
post = javap.split("public void postApply(", 1)[1]
assert "C2meLightTicketLevels.apply" not in pre and "C2meLightTicketLevels.apply" in post
receipt = {
 "status": "Compiled exact pending native acceptance, not final release",
 "product_commit": HEAD, "compile_run": RUN,
 "artifact_id": artifact["id"], "artifact_digest": artifact["digest"], "artifact_bytes": artifact["size_in_bytes"],
 "jar": JAR, "jar_bytes": len(jar_data), "jar_sha256": jar_sha, "reproducible": True,
 "actual_compiled_postapply_verified": True, "source_files": 998, "recipe_steps": 23,
 "focused_regression_suites": 23,
}
(out / "COMPILED-CANDIDATE-RECEIPT.json").write_text(json.dumps(receipt, indent=2) + "\n")
print("FINAL_COMPILED_CHECKPOINT_READY", json.dumps(receipt), flush=True)
