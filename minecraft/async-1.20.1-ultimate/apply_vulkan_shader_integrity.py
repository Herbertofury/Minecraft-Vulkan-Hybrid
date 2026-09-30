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
