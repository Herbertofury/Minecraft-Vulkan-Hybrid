import net.vulkanmod.vulkan.shader.parser.GlslComments;
import net.vulkanmod.vulkan.shader.parser.GlslStatements;

public final class GlslStatementsRegression {
    private static int cases;
    private static void check(String input, String expected) {
        String actual = GlslStatements.separate(input);
        if (!actual.equals(expected)) throw new AssertionError("Statement boundary mismatch: " + actual);
        cases++;
    }
    public static void main(String[] args) {
        check("uniform vec4 UserTint;out vec4 fragColor;void main(){fragColor=UserTint;}",
                "uniform vec4 UserTint;\nout vec4 fragColor;\nvoid main(){fragColor=UserTint;}");
        check("uniform vec4 UserTint;\r\nout vec4 fragColor;\r\n", "uniform vec4 UserTint;\r\nout vec4 fragColor;\r\n");
        check("void main(){for(int i=0;i<4;i++){float x=0;x++;} }", "void main(){for(int i=0;i<4;i++){float x=0;x++;} }");
        check("#define DECL uniform vec4 U;out vec4 C;\n", "#define DECL uniform vec4 U;out vec4 C;\n");
        check("#define DECL \\\nuniform vec4 U;out vec4 C;\nuniform float X;out float Y;",
                "#define DECL \\\nuniform vec4 U;out vec4 C;\nuniform float X;\nout float Y;");
        check("#moj_import \"name;with;semicolons.glsl\"\nuniform float X;out float Y;",
                "#moj_import \"name;with;semicolons.glsl\"\nuniform float X;\nout float Y;");
        check("uniform float X;   out float Y;  ", "uniform float X;\n   out float Y;  ");
        check("struct S {float x;float y;};uniform float X;", "struct S {float x;float y;};\nuniform float X;");
        check("uniform float X;", "uniform float X;");
        String commented = "uniform float X;/* ;{}() */out float Y;// ;{}()\nvoid main(){for(int i=0;i<2;i++){} }";
        String stripped = GlslComments.strip(commented);
        check(stripped, stripped.replace("X;", "X;\n"));
        System.out.println("GLSL_STATEMENTS_PASS cases=" + cases + " bodies_directives_and_conventional_lines_preserved=true");
    }
}
