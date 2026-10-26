#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: apply_mc263_provider_precedence.py <merged-upstream-root>')

root = Path(sys.argv[1]).resolve()
async_common = root / 'common/src/main/java/com/axalotl/async/common/AsyncCommon.java'
if not async_common.is_file():
    raise SystemExit(f'missing AsyncCommon.java: {async_common}')

text = async_common.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'source drift in {label}: expected exactly one marker, found {count}')
    text = text.replace(old, new, 1)


field_marker = '    public static boolean HARICHUNK = PlatformUtils.isModLoaded("harichunk") || PlatformUtils.isModLoaded("c2me") || PlatformUtils.isModLoaded("c2meforge");\n'
field_block = field_marker + '''    // Minecraft 26.3 backport provider ownership. These flags do not make the\n    // external mods dependencies; they only prevent Hari from competing for an\n    // invasive subsystem another established performance provider already owns.\n    public static boolean NOISIUM = PlatformUtils.isModLoaded("noisium");\n    public static boolean EXTERNAL_TERRAIN_RENDERER = PlatformUtils.isModLoaded("embeddium")\n            || PlatformUtils.isModLoaded("rubidium") || PlatformUtils.isModLoaded("sodium");\n    public static boolean EXTERNAL_SHADER_PIPELINE = PlatformUtils.isModLoaded("oculus")\n            || PlatformUtils.isModLoaded("iris");\n'''
replace_once(field_marker, field_block, '26.3 provider fields')

harichunk_block = '''        if (HARICHUNK) {\n            LOGGER.info("Detected: HariChunk/C2ME - Threading synchronized");\n            LOGGER.info("  -> Chunk operations deferred to C2ME async system");\n            LOGGER.info("  -> DynamicGraphMinFixedPoint excluded from SyncAll (C2ME manages lighting threads)");\n        }\n'''
provider_block = harichunk_block + '''        if (NOISIUM) {\n            LOGGER.info("Detected: Noisium - additive world-generation provider");\n            LOGGER.info("  -> Hari keeps its result-preserving 26.3 Cache2D fill lane; Noisium owns its generator/section fast paths");\n        }\n        if (EXTERNAL_TERRAIN_RENDERER) {\n            LOGGER.info("Detected: Embeddium/Rubidium/Sodium terrain renderer");\n            LOGGER.info("  -> Minecraft 26.3 MultiDrawIndirect terrain lane delegated to the external renderer");\n            LOGGER.info("  -> Hari will not install a competing terrain renderer");\n        } else {\n            LOGGER.info("Terrain renderer: vanilla 1.20.1 owner (Hari core remains renderer-neutral)");\n        }\n        if (EXTERNAL_SHADER_PIPELINE) {\n            LOGGER.info("Detected: Oculus/Iris shader pipeline");\n            LOGGER.info("  -> Minecraft 26.3 OIT/ShaderC client lanes delegated to the shader provider");\n            LOGGER.info("  -> Hari will not replace the active shader compiler/transparency pipeline");\n        } else {\n            LOGGER.info("Shader pipeline: vanilla 1.20.1 owner (Hari core does not force OIT/ShaderC)");\n        }\n'''
replace_once(harichunk_block, provider_block, '26.3 provider status logging')

old_none = '''        if (!(LITHIUM || HARIPLAYER || HARICHUNK)) {\n            LOGGER.info("No conflicting optimization mods detected - Full async mode enabled");\n        }\n'''
new_none = '''        if (!(LITHIUM || HARIPLAYER || HARICHUNK)) {\n            LOGGER.info("No conflicting server-threading optimization mods detected - Full async mode enabled");\n        }\n'''
replace_once(old_none, new_none, 'compatibility summary wording')

async_common.write_text(text, encoding='utf-8')

required = [
    'public static boolean NOISIUM',
    'public static boolean EXTERNAL_TERRAIN_RENDERER',
    'public static boolean EXTERNAL_SHADER_PIPELINE',
    'PlatformUtils.isModLoaded("embeddium")',
    'PlatformUtils.isModLoaded("oculus")',
    'MultiDrawIndirect terrain lane delegated',
    'OIT/ShaderC client lanes delegated',
]
final = async_common.read_text(encoding='utf-8')
missing = [token for token in required if token not in final]
if missing:
    raise SystemExit(f'26.3 provider-precedence invariant failed: missing {missing}')

print('HariMultiThread Minecraft 26.3 provider precedence applied successfully')
