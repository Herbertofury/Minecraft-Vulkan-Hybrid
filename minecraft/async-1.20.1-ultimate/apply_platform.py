#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_platform.py <merged-upstream-root>")

root = Path(sys.argv[1]).resolve()


def replace_exact(path: Path, old: str, new: str, count: int = 1) -> None:
    if not path.is_file():
        raise SystemExit(f"missing file: {path}")
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        raise SystemExit(
            f"source drift in {path.relative_to(root)}: expected {count}, found {found}: {old[:140]!r}"
        )
    path.write_text(text.replace(old, new, count), encoding="utf-8")


# Keep the release on the latest Forge 1.20.1 line used by the native QA gates.
properties = root / "gradle.properties"
replace_exact(properties, "forge_version=47.4.16", "forge_version=47.4.23")

# ForgeGradle 6 run configurations are not reliable in a multi-project build with
# Gradle configuration-on-demand enabled. Upstream enables it globally, which can
# leave the declared minecraft.runs.client configuration without its runClient task
# in a fresh Gradle invocation. Release/native QA needs deterministic run tasks.
replace_exact(properties, "org.gradle.configureondemand=true", "org.gradle.configureondemand=false")

# Upstream exposes enableGpuCollision in the common config and /async gpu toggle
# calls PlatformUtils.saveConfig(), but the Forge bridge never mirrored that value
# into ForgeConfigSpec. The setting therefore silently returned to its default on
# every JVM restart. Wire it through the same define/load/save lifecycle as the
# other runtime settings so the operator command actually persists on Forge.
forge_config = root / "forge/src/main/java/com/axalotl/async/forge/config/AsyncConfigForge.java"
replace_exact(
    forge_config,
    "        private static final ForgeConfigSpec.ConfigValue<Boolean> enableCircuitBreakerLocal;\n",
    "        private static final ForgeConfigSpec.ConfigValue<Boolean> enableCircuitBreakerLocal;\n"
    "        private static final ForgeConfigSpec.ConfigValue<Boolean> enableGpuCollisionLocal;\n",
)
replace_exact(
    forge_config,
    '''                enableCircuitBreakerLocal = BUILDER.comment("""\n                                Enable circuit breaker for entity tick crash isolation.\n                                When an entity type crashes repeatedly during async tick, it is automatically\n                                moved to synchronous ticking until it stabilizes. Prevents cascade failures.""")\n                                .define("enableCircuitBreaker", enableCircuitBreaker.getValue());\n\n''',
    '''                enableCircuitBreakerLocal = BUILDER.comment("""\n                                Enable circuit breaker for entity tick crash isolation.\n                                When an entity type crashes repeatedly during async tick, it is automatically\n                                moved to synchronous ticking until it stabilizes. Prevents cascade failures.""")\n                                .define("enableCircuitBreaker", enableCircuitBreaker.getValue());\n\n                enableGpuCollisionLocal = BUILDER.comment(\n                                "Enable Vulkan broad-phase acceleration for deferred entity push queries. " +\n                                "When disabled or unavailable, vanilla collision lookup remains authoritative.")\n                                .define("enableGpuCollision", enableGpuCollision.getValue());\n\n''',
)
replace_exact(
    forge_config,
    "                enableCircuitBreaker.setValue(enableCircuitBreakerLocal.get());\n",
    "                enableCircuitBreaker.setValue(enableCircuitBreakerLocal.get());\n"
    "                enableGpuCollision.setValue(enableGpuCollisionLocal.get());\n",
)
replace_exact(
    forge_config,
    "                enableCircuitBreakerLocal.set(enableCircuitBreaker.getValue());\n",
    "                enableCircuitBreakerLocal.set(enableCircuitBreaker.getValue());\n"
    "                enableGpuCollisionLocal.set(enableGpuCollision.getValue());\n",
)

print("Forge target updated to 1.20.1-47.4.23 with deterministic client runs and persistent GPU collision config")
