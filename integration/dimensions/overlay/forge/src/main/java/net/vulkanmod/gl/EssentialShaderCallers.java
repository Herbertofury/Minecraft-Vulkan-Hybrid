package net.vulkanmod.gl;

import org.objectweb.asm.tree.ClassNode;
import java.util.Map;

/** Exact reviewed original Elementa caller implementations, not a provider whitelist. */
public final class EssentialShaderCallers {
    private static final String PREFIX="gg/essential/elementa/shaders/";
    private static final Map<String,String> PINS=Map.of(
        PREFIX+"FloatUniform","656dd1dbac1627790b402157475218790e4589e70108f2cefcbd3fa129e3e27c",
        PREFIX+"IntUniform","bef215b1b4f6eac176ea5af383d95a9a119fa2a6783a22cbc5238622064fe6ad",
        PREFIX+"Shader","d5f63e730c32620b5a93f3c15b021274350779f6357f57bf2c854870a8881ff6",
        PREFIX+"Vec2Uniform","5a68a4923d17a98d87ceedf30577ec3d0b43246552525a21f78ce10e215343c3",
        PREFIX+"Vec4Uniform","77c36633312607a1e4268264288bfcd62d72fcdfd8020b509d91300b1180a950");
    private static final Map<String,Integer> CALLS=Map.of(PREFIX+"FloatUniform",2,PREFIX+"IntUniform",2,PREFIX+"Shader",16,PREFIX+"Vec2Uniform",2,PREFIX+"Vec4Uniform",2);
    private EssentialShaderCallers() {}
    public static boolean target(String name) { return PINS.containsKey(name.replace('.','/')); }
    public static ShaderCallerRemapper.Result apply(ClassNode target) {
        String pin=PINS.get(target.name);
        if(pin==null || !pin.equals(ShaderCallerRemapper.canonicalDigest(target)))
            throw new IllegalStateException("Native shader caller requires reviewed original bytecode: "+target.name);
        var result=ShaderCallerRemapper.apply(target);
        if(result.directCalls()!=CALLS.get(target.name) || result.methodHandles()!=0)
            throw new IllegalStateException("Native shader caller rewrite omitted or changed original API sites: "+target.name);
        return result;
    }
}
