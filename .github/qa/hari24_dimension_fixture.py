#!/usr/bin/env python3
"""Authoritative disposable dimension/mob/block-NBT test functions, from real JARs."""
from __future__ import annotations
import json
import re
import zipfile
from pathlib import Path

def install(world: Path, mods: Path, evidence: Path) -> list[dict]:
    pack = world / "datapacks" / "hari-dimension-qa"
    functions = pack / "data" / "hmtdim" / "functions"
    functions.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 15, "description": "Disposable native dimension/mob/persistence acceptance"}}) + "\n")
    (functions / "init.mcfunction").write_text("scoreboard objectives add hmtdim dummy\n")
    (functions / "portal_aether.mcfunction").write_text(
        "fill -4 99 -4 4 99 4 minecraft:stone\n"
        "fill -1 100 0 2 104 0 minecraft:glowstone\n"
        "fill 0 101 0 1 103 0 minecraft:air\n"
        "setblock 0 101 0 minecraft:water\n")
    # Return portal sits above the mob fixture, preserving the tested chest NBT.
    (functions / "portal_aether_return.mcfunction").write_text(
        "fill -4 139 -4 4 139 4 minecraft:stone\n"
        "fill -1 140 0 2 144 0 minecraft:glowstone\n"
        "fill 0 141 0 1 143 0 minecraft:air\n"
        "setblock 0 141 0 minecraft:water\n")
    catalogue = []
    for path in sorted(mods.glob("*.jar")):
        with zipfile.ZipFile(path) as jar:
            for namespace, dimension in (("aether", "the_aether"), ("midnight", "the_midnight")):
                language = f"assets/{namespace}/lang/en_us.json"
                if language not in jar.namelist():
                    continue
                strings = json.loads(jar.read(language))
                prefix = f"item.{namespace}."
                mobs = sorted(key[len(prefix):-len("_spawn_egg")] for key in strings
                              if key.startswith(prefix) and key.endswith("_spawn_egg")
                              and key.count(".") == 2)
                key = f"{namespace}:{dimension}"
                tag = f"hmtdim_{namespace}"
                # Native whirlwinds are temporary: 512..1023 ticks for passive,
                # 256..511 for evil. Their countdown and expiry are tested rather
                # than changing original AI or pretending they persist forever.
                transient = [m for m in mobs if namespace == "aether" and m in ("whirlwind", "evil_whirlwind")]
                persistent = [m for m in mobs if m not in transient]
                item = "aether:ambrosium_shard" if namespace == "aether" else "midnight:ebonite"
                # Find an ordinary existing item resource; no fabricated target identifier.
                if namespace == "midnight":
                    candidates = [k for k in strings if k.startswith("item.midnight.")
                                  and (k.endswith("ebonite_ingot") or k.endswith("nagrilite_ingot"))]
                    if not candidates:
                        raise RuntimeError("No known Midnight persistence item in actual language resources")
                    item = candidates[0].replace("item.midnight.", "midnight:")
                setup = ["forceload add -64 -64 64 64",
                         "fill -63 98 -63 63 98 63 minecraft:stone",
                         "fill -63 99 -63 63 101 -63 minecraft:glass",
                         "fill -63 99 63 63 101 63 minecraft:glass",
                         "fill -63 99 -62 -63 101 62 minecraft:glass",
                         "fill 63 99 -62 63 101 62 minecraft:glass",
                         f"kill @e[tag={tag}]",
                         f"kill @e[tag={tag}_transient]",
                         f'setblock 2 99 2 minecraft:chest{{Lock:"HariDimensionQA",Items:[{{Slot:0b,id:"{item}",Count:7b}}]}}']
                for i, mob in enumerate(mobs):
                    x, z = -24 + (i % 5) * 12, -24 + (i // 5) * 12
                    # AechorPlant.tick removes the plant on invalid ground,
                    # including stone; invulnerability intentionally does not
                    # bypass that original rule. Supply its native habitat.
                    if namespace == "aether" and mob == "aechor_plant":
                        setup.append(f"setblock {x} 98 {z} aether:aether_grass_block")
                    # A summon with explicit NBT skips native finalizeSpawn. Let
                    # Minecraft initialize the actual mob, then attach QA tags
                    # and protection within the same function/server tick.
                    selector = f"@e[type={namespace}:{mob},x={x},y=99,z={z},distance=..2,sort=nearest,limit=1]"
                    setup += [f"summon {namespace}:{mob} {x} 99 {z}",
                              f"tag {selector} add {tag}_{mob}",
                              f"tag @e[tag={tag}_{mob},limit=1] add {tag + '_transient' if mob in transient else tag}",
                              f"data merge entity @e[tag={tag}_{mob},limit=1] {{PersistenceRequired:1b,Invulnerable:1b}}"]
                    if mob in transient:
                        setup.append(f'execute store result score initial_{mob} hmtdim run data get entity @e[tag={tag}_{mob},limit=1] "Life Left"')
                setup += [f'say HMT_DIM_CREATED_{namespace}_{len(mobs)}']
                (functions / f"setup_{namespace}.mcfunction").write_text("\n".join(setup) + "\n")
                # Every type must exist, every entity must remain, and original mod item NBT survives.
                checks = ["scoreboard players set count hmtdim 0",
                          f"execute as @e[tag={tag}] run scoreboard players add count hmtdim 1"]
                condition = f"execute if dimension {key} if score count hmtdim matches {len(persistent)} "
                condition += " ".join(f"if entity @e[tag={tag}_{mob},limit=1]" for mob in persistent) + " "
                condition += f'if block 2 99 2 minecraft:chest{{Lock:"HariDimensionQA",Items:[{{Slot:0b,id:"{item}",Count:7b}}]}} '
                condition += f"run say HMT_DIM_VERIFIED_{namespace}_{len(persistent)}"
                checks.append(condition)
                (functions / f"verify_{namespace}.mcfunction").write_text("\n".join(checks) + "\n")
                audit = checks[:2] + ["scoreboard players get count hmtdim"]
                audit += [f"execute unless entity @e[tag={tag}_{mob},limit=1] run say HMT_DIM_MISSING_{namespace}_{mob}" for mob in persistent]
                audit += [f'execute unless block 2 99 2 minecraft:chest{{Lock:"HariDimensionQA",Items:[{{Slot:0b,id:"{item}",Count:7b}}]}} run say HMT_DIM_MISSING_NBT_{namespace}']
                (functions / f"audit_{namespace}.mcfunction").write_text("\n".join(audit) + "\n")
                if transient:
                    live_checks = ["scoreboard players set catalogue hmtdim 0",
                                   f"execute as @e[tag={tag}] run scoreboard players add catalogue hmtdim 1",
                                   f"execute as @e[tag={tag}_transient] run scoreboard players add catalogue hmtdim 1"]
                    for mob in transient:
                        live_checks.append(f'execute store result score remaining_{mob} hmtdim run data get entity @e[tag={tag}_{mob},limit=1] "Life Left"')
                    live = f"execute if dimension {key} if score catalogue hmtdim matches {len(mobs)} "
                    live += " ".join(f"if entity @e[tag={tag}_{mob},limit=1]" for mob in mobs) + " "
                    for mob in transient:
                        limits = "256..511" if mob == "evil_whirlwind" else "512..1023"
                        live += (f"if score initial_{mob} hmtdim matches {limits} "
                                 f"if score remaining_{mob} hmtdim matches 1.. "
                                 f"if score remaining_{mob} hmtdim < initial_{mob} hmtdim ")
                    live_checks.append(live + f"run say HMT_DIM_CATALOGUE_{namespace}_{len(mobs)}")
                    (functions / f"verify_catalogue_{namespace}.mcfunction").write_text("\n".join(live_checks)+"\n")
                    expired = f"execute if dimension {key} "
                    expired += " ".join(f"unless entity @e[tag={tag}_{mob},limit=1]" for mob in transient)
                    expired += f" run say HMT_DIM_NATIVE_LIFETIME_EXPIRED_{namespace}\n"
                    (functions / f"verify_expired_{namespace}.mcfunction").write_text(expired)
                for label, center, y in (("home", 0, 120), ("far", 4096, 140)):
                    positions = [(center + dx * 16, center + dz * 16)
                                 for dx in range(-3, 4) for dz in range(-3, 4)]
                    condition = f"execute if dimension {key} if entity @s[x={center-2},y={y-2},z={center-2},dx=4,dy=4,dz=4] "
                    condition += " ".join(f"if loaded {x} 80 {z}" for x, z in positions)
                    condition += f" run say HMT_DIM_CHUNKS_{namespace}_{label}_49\n"
                    (functions / f"ready_{namespace}_{label}.mcfunction").write_text(condition)
                catalogue.append({"namespace": namespace, "dimension": key, "mob_types": mobs,
                                  "expected_entities": len(persistent), "expected_catalogue": len(mobs),
                                  "persistent_mob_types": persistent, "transient_mob_types": transient,
                                  "persistence_item": item,
                                  "home": [0, 120, 0], "far": [4096, 140, 4096],
                                  "required_loaded_chunks": 49, "setup": setup, "checks": checks,
                                  "scope": "All spawn-egg mob types with original AI; native countdown/expiry for two temporary whirlwinds, persistent mob unload/save/reopen; not every boss combat phase"})
    if len({row["namespace"] for row in catalogue}) != len(catalogue):
        raise RuntimeError("Duplicate dimension provider")
    (evidence / "dimension-fixture.json").write_text(json.dumps(catalogue, indent=2) + "\n")
    return catalogue
