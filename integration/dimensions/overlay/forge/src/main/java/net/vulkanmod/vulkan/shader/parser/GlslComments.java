package net.vulkanmod.vulkan.shader.parser;

/** Removes GLSL comments before declaration tokenization, retaining positions and quoted imports. */
public final class GlslComments {
    private GlslComments() {}

    public static String strip(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean block = false, line = false, quoted = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (line) {
                if (c == '\n' || c == '\r') { line = false; out.append(c); }
                else out.append(' ');
            } else if (block) {
                if (c == '*' && next == '/') { out.append("  "); i++; block = false; }
                else out.append(c == '\n' || c == '\r' ? c : ' ');
            } else if (quoted) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '/' && (next == '/' || next == '*')) {
                out.append("  "); i++;
                line = next == '/'; block = next == '*';
            } else {
                out.append(c);
                if (c == '"') quoted = true;
            }
        }
        if (block) throw new IllegalArgumentException("Unterminated GLSL block comment");
        return out.toString();
    }
}
