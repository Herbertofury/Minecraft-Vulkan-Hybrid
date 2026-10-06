import net.vulkanmod.vulkan.shader.parser.GlslComments;

public final class GlslCommentsRegression {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String input="uniform sampler2D Sampler2; // lightmap\r\n"
                +"uniform/* animation/color noise */vec2 TrailNoiseOrigin;\n"
                +"/* out vec4 fake;\n uniform invalid ignored; */\n"
                +"#moj_import \"folder//name/*literal*/.glsl\"\n"
                +"void main(){float ratio=a/b; /* division */ gl_Position=vec4(ratio);}\n"
                +"// trailing comment";
        String actual=GlslComments.strip(input);
        check(actual.length()==input.length(),"source positions changed");
        for(int i=0;i<input.length();i++)if(input.charAt(i)=='\n'||input.charAt(i)=='\r')check(actual.charAt(i)==input.charAt(i),"line boundary changed");
        check(actual.contains("uniform sampler2D Sampler2;")&&actual.contains("vec2 TrailNoiseOrigin;"),"real declarations changed");
        check(!actual.contains("lightmap")&&!actual.contains("uniform invalid")&&!actual.contains("trailing comment"),"comment became shader code");
        check(actual.contains("\"folder//name/*literal*/.glsl\""),"quoted import changed");
        check(actual.contains("float ratio=a/b;")&&actual.contains("gl_Position=vec4(ratio);"),"shader operation changed");
        check(GlslComments.strip("\"a\\\"//b\" //gone").startsWith("\"a\\\"//b\""),"escaped quote changed");
        check(GlslComments.strip(actual).equals(actual),"comment stripping is not idempotent");
        try{GlslComments.strip("void main(){} /*");throw new AssertionError("unfinished comment accepted");}catch(IllegalArgumentException expected){}
        System.out.println("GLSL_DECLARATION_COMMENTS_IMPORTS_OPERATIONS_POSITIONS_PASS");
    }
}
