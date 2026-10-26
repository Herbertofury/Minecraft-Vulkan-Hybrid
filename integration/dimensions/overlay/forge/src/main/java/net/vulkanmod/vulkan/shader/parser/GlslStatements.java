package net.vulkanmod.vulkan.shader.parser;

/** Separates adjacent top-level statements without splitting function bodies or directives.
 * Comments must have been stripped first. This is a boundary scanner, not a GLSL grammar. */
public final class GlslStatements {
    private GlslStatements() {}

    public static String separate(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int braces = 0, parentheses = 0;
        boolean lineStart = true, directive = false, quoted = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            out.append(c);
            if (directive) {
                if (c == '\n') {
                    int previous = i - 1;
                    if (previous >= 0 && source.charAt(previous) == '\r') previous--;
                    directive = previous >= 0 && source.charAt(previous) == '\\';
                    lineStart = !directive;
                }
                continue;
            }
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '\n' || c == '\r') { lineStart = true; continue; }
            if (lineStart && Character.isWhitespace(c)) continue;
            if (lineStart && c == '#') { directive = true; lineStart = false; continue; }
            lineStart = false;
            if (c == '"') { quoted = true; continue; }
            if (c == '{') braces++;
            else if (c == '}') braces--;
            else if (c == '(') parentheses++;
            else if (c == ')') parentheses--;
            else if (c == ';' && braces == 0 && parentheses == 0) {
                int next = i + 1;
                while (next < source.length() && source.charAt(next) != '\n'
                        && source.charAt(next) != '\r' && Character.isWhitespace(source.charAt(next))) next++;
                if (next < source.length() && source.charAt(next) != '\n' && source.charAt(next) != '\r') {
                    out.append('\n');
                    lineStart = true;
                }
            }
        }
        return out.toString();
    }
}
