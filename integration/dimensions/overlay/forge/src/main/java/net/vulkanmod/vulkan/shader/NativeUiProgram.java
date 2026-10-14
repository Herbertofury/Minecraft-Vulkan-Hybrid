package net.vulkanmod.vulkan.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Original GPL-3.0-only frontend for paired legacy UI shaders. Produces actual
 * Vulkan SPIR-V and a reflected, shared uniform ABI without loading OpenGL.
 * This is deliberately a separate API: compiling a pair does not accept the
 * generic GL draw contract or supply framebuffer/texture/vertex state.
 */
public final class NativeUiProgram {
    private static final Pattern DECL = Pattern.compile(
            "\\b(uniform|varying|attribute|in|out)\\s+(float|int|vec[234]|ivec[234]|mat[234]|sampler2D)\\s+([A-Za-z_][A-Za-z_0-9]*)(\\s*\\[\\s*([1-9][0-9]*)\\s*])?\\s*;");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");
    private record Decl(String storage, String type, String name, int count) {
        String declaration() { return type + " " + name + (count == 0 ? "" : "[" + count + "]") + ";"; }
        boolean sameType(Decl other) { return type.equals(other.type) && count == other.count; }
    }
    private record Stage(String code, List<Decl> declarations, Set<String> identifiers) {}
    public record Sampler(String name, int binding) {}
    public record Attribute(String name, String type, int location) {}

    private final byte[] vertex, fragment;
    private final List<String> uniformNames;
    private final SpirvUniformBlock uniforms;
    private final List<Sampler> samplers;
    private final List<Attribute> attributes;

    private NativeUiProgram(byte[] vertex, byte[] fragment, List<String> names,
                            SpirvUniformBlock uniforms, List<Sampler> samplers, List<Attribute> attributes) {
        this.vertex = vertex; this.fragment = fragment; this.uniformNames = List.copyOf(names);
        this.uniforms = uniforms; this.samplers = List.copyOf(samplers); this.attributes = List.copyOf(attributes);
    }

    /** Returned buffers are independent caller-owned copies, never shaderc memory. */
    public ByteBuffer vertexSpirv() { return copy(vertex); }
    public ByteBuffer fragmentSpirv() { return copy(fragment); }
    public List<String> uniformNames() { return uniformNames; }
    public List<Sampler> samplers() { return samplers; }
    public List<Attribute> attributes() { return attributes; }
    public int uniformBytes() { return uniforms == null ? 0 : uniforms.bytes(); }
    public ByteBuffer allocateUniforms() { return uniforms == null ? ByteBuffer.allocateDirect(0) : uniforms.allocate(); }
    public void writeFloats(ByteBuffer target, String name, float... values) { uniforms.writeFloats(target, ordinal(name), values); }
    public void writeInts(ByteBuffer target, String name, int... values) { uniforms.writeInts(target, ordinal(name), values); }
    private int ordinal(String name) {
        int at = uniformNames.indexOf(name);
        if (at < 0) throw bad("Unknown linked uniform: " + name);
        return at;
    }

    /** Real stage compilation before a partner shader exists; link still checks the actual pair. */
    public static void validateStage(String name, String source, boolean vertex) {
        if (vertex) {
            compile(name, source, "void main(){gl_FragColor=vec4(1.0);}");
        } else {
            Stage stage = parse(source); StringBuilder partner = new StringBuilder();
            for (Decl d : stage.declarations) if (d.storage.equals("varying") || d.storage.equals("in")) {
                if (d.count != 0) throw bad("Varying arrays require another frontend");
                partner.append("varying ").append(d.declaration()).append('\n');
            }
            partner.append("void main(){gl_Position=gl_Vertex;");
            for (Decl d : stage.declarations) if (d.storage.equals("varying") || d.storage.equals("in"))
                partner.append(d.name).append('=').append(d.type).append("(0);");
            partner.append('}');compile(name, partner.toString(), source);
        }
    }

    public static NativeUiProgram compile(String name, String vertexSource, String fragmentSource) {
        Objects.requireNonNull(name); Stage v = parse(vertexSource), f = parse(fragmentSource);
        LinkedHashMap<String, Decl> u = new LinkedHashMap<>(), inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
        for (Decl d : v.declarations) {
            switch (d.storage) {
                case "uniform" -> merge(u, d);
                case "attribute", "in" -> merge(inputs, d);
                case "varying", "out" -> merge(outputs, d);
                default -> throw bad("Unsupported vertex declaration");
            }
        }
        for (Decl d : f.declarations) {
            switch (d.storage) {
                case "uniform" -> merge(u, d);
                case "varying", "in" -> {
                    Decl from = outputs.get(d.name);
                    if (from == null || !from.sameType(d)) throw bad("Unmatched fragment input: " + d.name);
                }
                default -> throw bad("Explicit fragment outputs require another frontend");
            }
        }
        if (v.identifiers.contains("gl_ModelViewProjectionMatrix"))
            merge(u, new Decl("uniform", "mat4", "mvh_ModelViewProjection", 0));
        boolean color = f.identifiers.contains("gl_Color") || v.identifiers.contains("gl_FrontColor");
        if (v.identifiers.contains("gl_Vertex")) merge(inputs, new Decl("in", "vec4", "mvh_Vertex", 0));
        if (v.identifiers.contains("gl_Color") || color) merge(inputs, new Decl("in", "vec4", "mvh_Color", 0));
        if (v.identifiers.contains("gl_MultiTexCoord0")) merge(inputs, new Decl("in", "vec4", "mvh_TexCoord", 0));
        if (color) merge(outputs, new Decl("out", "vec4", "mvh_FrontColor", 0));
        List<Decl> ordinary = u.values().stream().filter(d -> !d.type.equals("sampler2D")).toList();
        List<Sampler> samplers = new ArrayList<>(); List<Attribute> attributes = new ArrayList<>();
        StringBuilder common = new StringBuilder("#version 450\n");
        if (!ordinary.isEmpty()) {
            common.append("layout(set=0,binding=0,std140) uniform MvhUiUniforms {\n");
            for (Decl d : ordinary) common.append(d.declaration()).append('\n');
            common.append("};\n");
        }
        for (Decl d : u.values()) if (d.type.equals("sampler2D")) {
            if (d.count != 0) throw bad("Sampler arrays require another frontend");
            int binding = samplers.size() + 1; samplers.add(new Sampler(d.name, binding));
            common.append("layout(set=0,binding=").append(binding).append(") uniform ").append(d.declaration()).append('\n');
        }
        StringBuilder vi = new StringBuilder(), vo = new StringBuilder(), fi = new StringBuilder();
        int location = 0;
        for (Decl d : inputs.values()) {
            if (d.count != 0 || d.type.startsWith("mat")) throw bad("Array/matrix vertex inputs require another frontend");
            attributes.add(new Attribute(d.name, d.type, location));
            vi.append("layout(location=").append(location++).append(") in ").append(d.declaration()).append('\n');
        }
        location = 0;
        for (Decl d : outputs.values()) {
            if (d.count != 0 || d.type.startsWith("mat") || d.type.startsWith("i")) throw bad("Unsupported varying layout/interpolation");
            vo.append("layout(location=").append(location).append(") out ").append(d.declaration()).append('\n');
            fi.append("layout(location=").append(location++).append(") in ").append(d.declaration()).append('\n');
        }
        Map<String, String> vr = Map.of("gl_Vertex", "mvh_Vertex", "gl_Color", "mvh_Color", "gl_FrontColor", "mvh_FrontColor",
                "gl_MultiTexCoord0", "mvh_TexCoord", "gl_ModelViewProjectionMatrix", "mvh_ModelViewProjection", "texture2D", "texture");
        Map<String, String> fr = Map.of("gl_Color", "mvh_FrontColor", "gl_FragColor", "mvh_FragColor", "texture2D", "texture");
        String vs = common + vi.toString() + vo + rewrite(v.code, vr, true)
                + "\nvoid main(){" + (color ? "mvh_FrontColor=mvh_Color;" : "")
                + "mvh_legacy_main();gl_Position.z=(gl_Position.z+gl_Position.w)*0.5;}\n";
        String fs = common + fi.toString() + "layout(location=0) out vec4 mvh_FragColor;\n" + rewrite(f.code, fr, false);
        // The two owners are closed even if the second compilation/reflection fails.
        try (SPIRVUtils.SPIRV vert = SPIRVUtils.compileShader(name + ".vsh", vs, SPIRVUtils.ShaderKind.VERTEX_SHADER);
             SPIRVUtils.SPIRV frag = SPIRVUtils.compileShader(name + ".fsh", fs, SPIRVUtils.ShaderKind.FRAGMENT_SHADER)) {
            byte[] vb = bytes(vert.bytecode()), fb = bytes(frag.bytecode());
            SpirvUniformBlock block = null;
            if (!ordinary.isEmpty()) {
                // Performance optimization may remove an entirely unused stage block.
                SpirvUniformBlock a = reflectIfPresent(vb), b = reflectIfPresent(fb);
                if (a == null && b == null) throw bad("No active uniform block in either compiled stage");
                if (a != null && b != null && (!a.fields().equals(b.fields()) || a.bytes() != b.bytes()))
                    throw bad("Compiled stage uniform ABI mismatch");
                block = a != null ? a : b;
                if (block.fields().size() != ordinary.size()) throw bad("Compiled uniform member count mismatch");
            }
            return new NativeUiProgram(vb, fb, ordinary.stream().map(Decl::name).toList(), block, samplers, attributes);
        }
    }

    private static SpirvUniformBlock reflectIfPresent(byte[] code) {
        try { return SpirvUniformBlock.reflect(copy(code), 0, 0); }
        catch (IllegalArgumentException e) {
            if (!e.getMessage().endsWith("Uniform descriptor absent")) throw e;
            return null;
        }
    }
    private static void merge(Map<String, Decl> into, Decl d) {
        Decl previous = into.putIfAbsent(d.name, d);
        if (previous != null && !previous.sameType(d)) throw bad("Conflicting declaration: " + d.name);
        if (d.name.startsWith("mvh_") && !d.name.equals("mvh_ModelViewProjection") && !d.name.equals("mvh_Vertex") && !d.name.equals("mvh_Color") && !d.name.equals("mvh_TexCoord") && !d.name.equals("mvh_FrontColor"))
            throw bad("Reserved native frontend identifier");
    }
    private static Stage parse(String source) {
        Objects.requireNonNull(source);
        if (source.length() > 4 * 1024 * 1024) throw bad("Shader source extent unsupported");
        String code = comments(source).replaceAll("(?m)^\\s*#version[^\\r\\n]*", "");
        if (code.contains("#")) throw bad("Preprocess includes/macros before the legacy UI frontend");
        Set<String> ids = new HashSet<>(); Matcher im = IDENT.matcher(code); while (im.find()) ids.add(im.group());
        if (ids.stream().anyMatch(x -> x.startsWith("mvh_"))) throw bad("Reserved native frontend identifier");
        List<Decl> declarations = new ArrayList<>(); Set<String> declared = new HashSet<>(); Matcher dm = DECL.matcher(code); StringBuffer body = new StringBuffer();
        while (dm.find()) {
            // Declarations must be top-level; a function parameter is not a uniform.
            int depth = 0; for (int i = 0; i < dm.start(); i++) { char c = code.charAt(i); if (c == '{') depth++; if (c == '}') depth--; }
            if (depth != 0) throw bad("Non-global interface declaration");
            int count = dm.group(5) == null ? 0 : Integer.parseInt(dm.group(5));
            if (count > 65536) throw bad("Uniform array extent unsupported");
            if (!declared.add(dm.group(3))) throw bad("Duplicate interface declaration: " + dm.group(3));
            declarations.add(new Decl(dm.group(1), dm.group(2), dm.group(3), count));
            dm.appendReplacement(body, " ".repeat(dm.end() - dm.start()));
        }
        dm.appendTail(body);
        return new Stage(body.toString(), declarations, ids);
    }
    private static String rewrite(String body, Map<String, String> replacements, boolean vertex) {
        Matcher m = IDENT.matcher(body); StringBuffer out = new StringBuffer();
        while (m.find()) {
            String id = m.group(), replacement = replacements.get(id);
            if (id.startsWith("gl_") && replacement == null && !Set.of("gl_Position", "gl_FragCoord", "gl_FrontFacing", "gl_PointSize").contains(id))
                throw bad("Unsupported legacy builtin: " + id);
            if (vertex && id.equals("main")) replacement = "mvh_legacy_main";
            m.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? id : replacement));
        }
        m.appendTail(out); return out.toString();
    }
    private static String comments(String source) {
        StringBuilder b = new StringBuilder(source); boolean line = false, block = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i), next = i + 1 < source.length() ? source.charAt(i + 1) : 0;
            if (!line && !block && c == '/' && next == '/') { line = true; b.setCharAt(i, ' '); continue; }
            if (!line && !block && c == '/' && next == '*') { block = true; b.setCharAt(i, ' '); b.setCharAt(++i, ' '); continue; }
            if (block && c == '*' && next == '/') { b.setCharAt(i, ' '); b.setCharAt(++i, ' '); block = false; continue; }
            if (c == '\n' || c == '\r') { line = false; continue; }
            if (line || block) b.setCharAt(i, ' ');
            else if (c == '"') throw bad("GLSL string/include requires preprocessing");
        }
        if (block) throw bad("Unterminated shader comment");
        return b.toString();
    }
    private static byte[] bytes(ByteBuffer value) { ByteBuffer v = value.duplicate(); byte[] b = new byte[v.remaining()]; v.get(b); return b; }
    private static ByteBuffer copy(byte[] value) { return ByteBuffer.allocateDirect(value.length).order(ByteOrder.LITTLE_ENDIAN).put(value).flip(); }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException("Native UI shader: " + message); }
}
