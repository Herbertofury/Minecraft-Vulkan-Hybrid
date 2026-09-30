#!/usr/bin/env python3
"""Repair Forge shader resolution without swallowing errors or substituting shading."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
path = root / "forge/src/main/java/net/vulkanmod/mixin/render/ShaderInstanceM.java"
s = path.read_text()
assert s.count("private void createFallbackShader(") == 1, "unexpected shader source"
s = s.replace("import com.mojang.blaze3d.vertex.DefaultVertexFormat;\n", "")
s = s.replace("import net.vulkanmod.vulkan.shader.Pipeline;", "import net.vulkanmod.vulkan.shader.Pipeline;\nimport net.vulkanmod.vulkan.shader.ShaderResources;")
s = s.replace("    private String vsPath;\n    private String fsName;\n", "")
s = s.replace("createLegacyShader(resourceProvider, format);", "createLegacyShader(resourceProvider, shaderLocation, format);")
s = s.replace('            Initializer.LOGGER.error("Error on shader {} creation", name, e);\n            createFallbackShader(format);', '            throw new IllegalStateException("Cannot create Vulkan shader " + shaderLocation, e);')
a = s.index("    // Bundled Vulkan shaders for mod namespaces")
b = s.index("    private void wireUnresolvedUniforms()", a)
s = s[:a] + "    // Mod-owned uniforms must retain their real values; a missing binding is a shader defect.\n" + s[b:]
a = s.index("                    Initializer.LOGGER.warn(\"No supplier or ShaderInstance uniform")
b = s.index("                }", a)
s = s[:a] + '                    throw new IllegalStateException("No supplier or ShaderInstance uniform for "\n                            + vUniform.getName() + " in shader " + this.f_173300_);\n' + s[b:]
a = s.index("        if (this.f_173300_.contains(", s.index("private Program loadNames"))
b = s.index("        return null;", a)
s = s[:a] + "        // Program names are resolved from JSON at constructor return, after uniforms exist.\n" + s[b:]
s = s.replace('                Initializer.LOGGER.error(String.format("Error: field %s not present in uniform map", vUniform.getName()));\n                continue;', '                throw new IllegalStateException("Uniform " + vUniform.getName()\n                        + " missing from shader " + this.f_173300_);')
a = s.index("    private void createLegacyShader(")
b = s.index("            GlslConverter converter", a)
s = s[:a] + '    private void createLegacyShader(ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat format) throws java.io.IOException {\n            ShaderResources.Programs programs = ShaderResources.resolve(resourceProvider, shaderLocation);\n            String vshSrc = ShaderResources.read(resourceProvider, programs.vertex());\n            String fshSrc = ShaderResources.read(resourceProvider, programs.fragment());\n\n' + s[b:]
a = s.index("        } catch (Throwable e)", a)
b = s.index("    private static String getVulkanShaderPath", a)
s = s[:a] + "    }\n\n" + s[b:]
s = s.replace('        return Pipeline.class.getResourceAsStream(resourcePath) != null ? path : null;', '        try (InputStream resource = Pipeline.class.getResourceAsStream(resourcePath)) {\n            return resource != null ? path : null;\n        } catch (java.io.IOException failure) {\n            throw new java.io.UncheckedIOException(failure);\n        }')
path.write_text(s)
rel = Path("net/vulkanmod/vulkan/shader/ShaderResources.java")
destination = root / "forge/src/main/java" / rel
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(here / "vulkan-hybrid-src" / rel, destination)
print("Shader integrity: active JSON/imports, closed streams, exact bindings, no approximate fallback")

# Simulation failures must not be logged and treated as successful ticks.
processor = root / "common/src/main/java/com/axalotl/async/common/ParallelProcessor.java"
p = processor.read_text()
p = p.replace("        ConcurrentLinkedQueue<T> work = new ConcurrentLinkedQueue<>(items);", "        TaskFailures failures = new TaskFailures();\n        ConcurrentLinkedQueue<T> work = new ConcurrentLinkedQueue<>(items);", 1)
p = p.replace("                while ((item = work.poll()) != null) {", "                while (!failures.failed() && (item = work.poll()) != null) {", 1)
p = p.replace('                LOGGER.error("Error during parallel batch item", t);', '                failures.record(t);', 1)
a = p.index("        waitForFutures(futures, waitWorld);", p.index("public static <T> void forEachParallel"))
p = p[:a] + p[a:].replace("        waitForFutures(futures, waitWorld);", "        waitForFutures(futures, waitWorld);\n        failures.rethrow();", 1)
p = p.replace("        boolean allDone;\n", "        boolean interrupted = false;\n        boolean allDone;\n", 1)
p = p.replace("        do {\n            allDone = futures", "        do {\n            if (Thread.interrupted()) interrupted = true;\n            allDone = futures", 1)
a = p.index("        // Collect results and log errors")
b = p.index("    public static boolean shouldTickSynchronously", a)
p = p[:a] + "        if (interrupted) Thread.currentThread().interrupt();\n        TaskFailures.joinCompleted(futures);\n    }\n\n" + p[b:]
a = p.index("        } catch (Exception e)", p.index("private static void tickEntity"))
b = p.index("        } finally {", a)
p = p[:b] + "            if (e instanceof RuntimeException runtime) throw runtime;\n            throw new IllegalStateException(\"Entity tick failed\", e);\n" + p[b:]
assert 'LOGGER.error("Error during parallel batch item", t)' not in p
processor.write_text(p)
rel = Path("com/axalotl/async/common/TaskFailures.java")
destination = root / "common/src/main/java" / rel
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(here / "vulkan-hybrid-src" / rel, destination)
print("Simulation integrity: join active workers and propagate tick/task failures")

# A failed world tick must still release deferral state after all active workers join.
p = processor.read_text()
assert p.count('        boolean workersJoined = false;') == 1
p = p.replace('        boolean workersJoined = false;', '        boolean workersJoined = false;\n        TaskFailures batchFailures = new TaskFailures();', 1)
old = '''        } finally {
            if (pushBatchActive) {
                // If an unexpected exception bypassed the normal join, converge any
                // already-submitted workers before exposing their final positions.
                if (!workersJoined) waitForFutures(futures, world);
                GpuPushBatch.endBatch(world);
                GpuPushBatch.flush(world);
            }
        }
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();'''
new = '''        } catch (Throwable failure) {
            batchFailures.record(failure);
        } finally {
            if (pushBatchActive) {
                if (!workersJoined) {
                    try {
                        waitForFutures(futures, world);
                    } catch (Throwable failure) {
                        batchFailures.record(failure);
                    }
                }
                GpuPushBatch.endBatch(world);
                if (batchFailures.failed()) GpuPushBatch.discard(world);
                else GpuPushBatch.flush(world);
            }
        }
        batchFailures.rethrow();
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();'''
assert p.count(old) == 1, 'world-batch failure cleanup source drift'
p = p.replace(old, new, 1)
processor.write_text(p)
push = root / 'common/src/main/java/com/axalotl/async/common/gpu/GpuPushBatch.java'
p = push.read_text()
anchor = '    public static boolean isBatchActive(ServerLevel world) {'
assert p.count(anchor) == 1
p = p.replace(anchor, '''    /** Drop incomplete-tick deferrals only after active workers have converged. */
    public static void discard(ServerLevel world) {
        if (world == null) return;
        if (isBatchActive(world)) throw new IllegalStateException("Cannot discard an active entity batch");
        DEFERRED.remove(world.dimension());
    }

''' + anchor, 1)
p = p.replace('''            LOGGER.error("Refusing to replay deferred pushes while the entity batch is still active for {}",
                    world.dimension().location());
            return;''', '''            throw new IllegalStateException("Cannot replay deferred pushes during active batch " + world.dimension().location());''', 1)
push.write_text(p)

# Opt-in native failure probe is not registered during ordinary play.
rel = Path("com/axalotl/async/forge/qa/HariServerFailureProbe.java")
destination = root / "forge/src/main/java" / rel
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(here / "vulkan-hybrid-src" / rel, destination)
forge = root / "forge/src/main/java/com/axalotl/async/forge/AsyncForge.java"
s = forge.read_text()
anchor = "        MinecraftForge.EVENT_BUS.register(this);"
assert s.count(anchor) == 1
s = s.replace(anchor, anchor + "\n        if (Boolean.getBoolean(\"harimt.qa.entityFault\")) {\n            MinecraftForge.EVENT_BUS.register(new com.axalotl.async.forge.qa.HariServerFailureProbe());\n        }", 1)
forge.write_text(s)
