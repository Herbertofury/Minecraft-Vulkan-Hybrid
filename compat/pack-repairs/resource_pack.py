"""Make a private, hash-pinned resource overlay from the user's existing mods."""
from pathlib import Path
import argparse
import hashlib
import json
import struct
import zipfile

MIDNIGHT = "b2a542a21f49d49d8f7b797af8de0290dd17e4a6fb1baa9cb98c0f44408766d4"
MOWZIE = "e8ce1768cda6f1e1fadd2321c92921b473bd0cee45ba4b8387fb30f8326cb31c"
WOODS = ("shadowroot", "dark_willow", "manglewood", "nightshroom", "dewshroom", "viridshroom", "moonshroom", "bogshroom")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def pinned(path, expected):
    assert not path.is_symlink(), "Symlink input rejected"
    data = path.read_bytes()
    assert digest(data) == expected, "Different input requires a new audit"
    return data


def make(midnight, mowzie, output):
    assert output.suffix.lower() == ".zip" and not output.exists() and not output.with_suffix(".manifest.json").exists(), "Use a fresh ZIP and manifest output"
    original = {midnight: pinned(midnight, MIDNIGHT), mowzie: pinned(mowzie, MOWZIE)}
    entries = {}
    changes = []
    with zipfile.ZipFile(midnight) as z:
        for wood in WOODS:
            source = "assets/midnight/textures/block_entity/sign/" + ("dark_wilow" if wood == "dark_willow" else wood) + ".png"
            target = "assets/midnight/textures/entity/signs/" + wood + ".png"
            data = z.read(source)
            assert data[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", data[16:24]) == (64, 32)
            entries[target] = data
            changes.append({"target": target, "source": source, "operation": "byte-identical texture alias", "sha256": digest(data)})
    with zipfile.ZipFile(mowzie) as z:
        for model in ("diamond_grottol", "black_pink_grottol"):
            target = "assets/mowziesmobs/models/block/" + model + ".json"
            before = json.loads(z.read(target))
            after = json.loads(json.dumps(before))
            comment = after["textures"].pop("__comment")
            assert comment == "for LLibrary bug not putting particle into textures set" and "__comment" not in after
            after["__comment"] = comment
            # Reversal must reproduce every original property and value.
            reversed_model = json.loads(json.dumps(after))
            reversed_model["textures"]["__comment"] = reversed_model.pop("__comment")
            assert reversed_model == before
            entries[target] = (json.dumps(after, indent=2) + "\n").encode()
            changes.append({"target": target, "operation": "move non-texture comment to model root", "source_sha256": digest(z.read(target)), "sha256": digest(entries[target])})
    manifest = {"schema": 1, "status": "Resource correctness repair; native runtime validation separate", "inputs": [{"filename": p.name, "sha256": digest(data)} for p, data in original.items()], "changes": changes}
    entries["pack.mcmeta"] = (json.dumps({"pack": {"pack_format": 15, "description": "Hari Noxviola resource compatibility: original sign textures and Grottol model metadata"}}, indent=2) + "\n").encode()
    entries["MVH-RESOURCE-REPAIR.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    entries["ATTRIBUTION.txt"] = b"Private local compatibility overlay. Midnight textures remain The Midnight team's original copyrighted assets. Mowzie's Mobs models remain Bob Mowzie's original copyrighted assets. Original licenses and authorship remain in the source JARs. This overlay is generated locally from owned, hash-pinned files; do not redistribute it. The public generator is GPL-3.0-only. No pixels, UVs, geometry or functional model properties change.\n"
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)
    with zipfile.ZipFile(output) as z:
        assert z.testzip() is None and set(z.namelist()) == set(entries)
        assert all(z.read(name) == data for name, data in entries.items())
        assert json.loads(z.read("pack.mcmeta"))["pack"]["pack_format"] == 15
    assert all(p.read_bytes() == data for p, data in original.items()), "Source changed during generation"
    manifest["output_sha256"] = digest(output.read_bytes())
    report = output.with_suffix(".manifest.json")
    with report.open("x", encoding="utf8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print(json.dumps({"output": str(output), "sha256": manifest["output_sha256"], "repairs": len(changes), "pack_format": 15}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("midnight", type=Path)
    parser.add_argument("mowzie", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    make(args.midnight, args.mowzie, args.output)
