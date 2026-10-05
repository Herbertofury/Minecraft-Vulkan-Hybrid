package mvhflywheelbackend;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.backend.BackendConfig;
import dev.engine_room.flywheel.backend.MaterialShaderIndices;
import dev.engine_room.flywheel.backend.compile.component.*;
import dev.engine_room.flywheel.backend.engine.uniform.FrameUniforms;
import dev.engine_room.flywheel.backend.glsl.*;
import dev.engine_room.flywheel.backend.glsl.generate.*;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import net.minecraft.resources.ResourceLocation;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

/** Original material/instance GLSL graph with explicit Vulkan descriptor namespaces. */
public final class NativeShaderSources {
    private static volatile ShaderSources sources;
    private static final AtomicLong GENERATION=new AtomicLong();
    private static final Map<Key,Code> CACHE=new HashMap<>();
    private static final Map<InstanceType<?>,ComputeCode> COMPUTE_CACHE=new IdentityHashMap<>();
    public record Code(byte[] vertex,byte[] fragment,long generation) {}
    public record ComputeCode(byte[] cull,byte[] apply,long generation) {}
    private record Key(InstanceType<?> type,Object shaders,Object fog,Object cutout,Object light,
                       boolean embedded,boolean crumble,boolean debug,String smooth) {}
    public static synchronized void reload(ShaderSources next){sources=Objects.requireNonNull(next);invalidate();}
    public static synchronized void invalidate(){CACHE.clear();COMPUTE_CACHE.clear();GENERATION.incrementAndGet();}
    public static long generation(){return GENERATION.get();}
    private static ResourceLocation rl(String p){return new ResourceLocation("flywheel",p);}
    /** Original generic instance sphere transform and packed frustum test, with native indirect counts.
     * This conservative stage does not yet perform depth-pyramid occlusion; it never culls by an invented depth.
     */
    public static synchronized ComputeCode compute(InstanceType<?> type){
        if(sources==null)throw new IllegalStateException("Flywheel compute resources have not reloaded");
        var cached=COMPUTE_CACHE.get(type);if(cached!=null)return cached;
        if(COMPUTE_CACHE.size()>=256)throw new IllegalStateException("Native culling shader cache bound exceeded");
        String original=sources.get(rl("internal/indirect/cull.glsl")).source();
        int start=original.indexOf("bool _flw_testSphere("),end=original.indexOf("bool projectSphere(",start);
        if(start<0||end<=start)throw new IllegalStateException("Pinned original frustum helper not found");
        String cull="#version 450\n"+graph(List.of(sources.get(rl("internal/indirect/cull_api_impl.glsl")),new InstanceStructComponent(type),
            sources.get(type.cullShader()),new SsboInstanceComponent(type),sources.get(rl("util/matrix.glsl")),
            sources.get(rl("internal/indirect/model_descriptor.glsl")),sources.get(rl("internal/indirect/matrices.glsl"))))+original.substring(start,end)+"""
            layout(local_size_x=32) in;
            layout(std430,set=1,binding=1) restrict writeonly buffer NativeCullTargets { uint _mvh_targets[]; };
            layout(std430,set=1,binding=4) restrict buffer NativeCullModels { ModelDescriptor _mvh_models[]; };
            layout(std430,set=1,binding=6) restrict readonly buffer NativeCullPages { uint _mvh_pages[]; };
            layout(std430,set=1,binding=7) restrict readonly buffer NativeCullMatrices { Matrices _mvh_matrices[]; };
            void main(){
                uint page=gl_WorkGroupID.x*2u;
                if(page>=_mvh_pages.length())return;
                if((_mvh_pages[page+1u]&(1u<<gl_LocalInvocationID.x))==0u)return;
                uint model=_mvh_pages[page],index=gl_GlobalInvocationID.x;
                vec3 center;float radius;
                _flw_unpackBoundingSphere(_mvh_models[model].boundingSphere,center,radius);
                FlwInstance instance=_flw_unpackInstance(index);
                flw_transformBoundingSphere(instance,center,radius);
                uint matrix=_mvh_models[model].matrixIndex;
                if(matrix>0u)transformBoundingSphere(_mvh_matrices[matrix].pose,center,radius);
                if(_flw_testSphere(center,radius)){
                    uint target=atomicAdd(_mvh_models[model].instanceCount,1u);
                    _mvh_targets[_mvh_models[model].baseInstance+target]=index;
                }
            }
            """;
        cull=adaptUniforms(cull).replaceAll("layout\\(std430, binding = 1\\)","layout(std430,set=1,binding=0)");
        String apply="#version 450\n#define _FLW_SUBGROUP_SIZE 32\n"+graph(List.of(sources.get(rl("internal/indirect/apply.glsl"))));
        apply=apply.replaceAll("layout\\(std430, binding = _FLW_MODEL_BUFFER_BINDING\\)","layout(std430,set=1,binding=4)")
            .replaceAll("layout\\(std430, binding = _FLW_DRAW_BUFFER_BINDING\\)","layout(std430,set=1,binding=5)");
        var code=new ComputeCode(compile(cull,shaderc_compute_shader),compile(apply,shaderc_compute_shader),generation());COMPUTE_CACHE.put(type,code);return code;
    }
    public static synchronized Code get(InstanceType<?> type,Material material,boolean embedded,boolean crumble){
        if(sources==null)throw new IllegalStateException("Flywheel shader resources have not reloaded");
        MaterialShaderIndices.fogIndex(material.fog());MaterialShaderIndices.cutoutIndex(material.cutout());
        String smooth=BackendConfig.INSTANCE.lightSmoothness().name();
        Key key=new Key(type,material.shaders(),material.fog(),material.cutout(),material.light(),embedded,crumble,FrameUniforms.debugOn(),smooth);
        Code cached=CACHE.get(key);if(cached!=null)return cached;
        if(CACHE.size()>=1024)throw new IllegalStateException("Native material shader cache bound exceeded");
        String header="#version 450\n#define _FLW_LIGHT_LUT_BUFFER_BINDING 2\n#define _FLW_LIGHT_SECTIONS_BUFFER_BINDING 3\n";
        if(embedded)header+="#define FLW_EMBEDDED\n";
        if(crumble)header+="#define _FLW_CRUMBLING\n";
        if(key.debug)header+="#define _FLW_DEBUG\n";
        int smoothness=BackendConfig.INSTANCE.lightSmoothness().ordinal();
        header+="#define _FLW_LIGHT_SMOOTHNESS "+Math.min(smoothness,2)+"\n";
        if(smoothness==3)header+="#define _FLW_INNER_FACE_CORRECTION\n";
        String vertex=header+graph(List.of(sources.get(rl("internal/api_impl.vert")),new InstanceStructComponent(type),
            sources.get(type.vertexShader()),sources.get(material.shaders().vertexSource()),sources.get(rl("internal/vertex_input.vert")),
            new SsboInstanceComponent(type),sources.get(rl("internal/instancing/main.vert"))));
        SourceComponent fog=UberShaderComponent.builder(rl("fog"))
            .materialSources(MaterialShaderIndices.fogSources().all())
            .adapt(FnSignature.create().returnType("vec4").name("flw_fogFilter").arg("vec4","color").build(),GlslExpr.variable("color"))
            .switchOn(GlslExpr.variable("_flw_uberFogIndex")).build(sources);
        SourceComponent cutout=UberShaderComponent.builder(rl("cutout"))
            .materialSources(MaterialShaderIndices.cutoutSources().all())
            .adapt(FnSignature.create().returnType("bool").name("flw_discardPredicate").arg("vec4","color").build(),GlslExpr.boolLiteral(false))
            .switchOn(GlslExpr.variable("_flw_uberCutoutIndex")).build(sources);
        String fragment=header+(material.cutout()!=CutoutShaders.OFF?"#define _FLW_USE_DISCARD\n":"")+
            graph(List.of(sources.get(rl("internal/api_impl.frag")),sources.get(material.shaders().fragmentSource()),
            sources.get(rl("internal/components_header.frag")),fog,sources.get(material.light().source()),cutout,sources.get(rl("internal/instancing/main.frag"))));
        vertex=adapt(vertex,true);fragment=adapt(fragment,false);
        Code code=new Code(compile(vertex,true),compile(fragment,false),generation());CACHE.put(key,code);return code;
    }
    private static String graph(List<SourceComponent> roots){StringBuilder b=new StringBuilder();Set<String> seen=new HashSet<>();for(var c:roots)append(c,seen,b);return b.toString();}
    private static void append(SourceComponent c,Set<String> seen,StringBuilder b){
        // The original SSBO light functions replace only the storage transport, retaining the same LUT shader.
        if(c.name().contains("internal/instancing/light.glsl"))c=sources.get(rl("internal/indirect/light.glsl"));
        if(!seen.add(c.name()))return;for(var d:c.included())append(d,seen,b);b.append("\n// ").append(c.name()).append('\n').append(c.source()).append('\n');
    }
    private static String adapt(String src,boolean vertex){
        src=src.replace("gl_VertexID","gl_VertexIndex").replace("gl_InstanceID","gl_InstanceIndex");
        src=src.replaceAll("uniform\\s+uvec2\\s+_flw_packedMaterial\\s*;","")
            .replaceAll("uniform\\s+int\\s+_flw_baseInstance\\s*=\\s*0\\s*;","")
            .replaceAll("uniform\\s+(?:uint|mat4|mat3)\\s+(?:_flw_vertexOffset|_flw_modelMatrixUniform|_flw_normalMatrixUniform)\\s*;","");
        String draw="layout(std140,set=0,binding=5) uniform NativeDraw { uvec2 _flw_packedMaterial; uint _flw_vertexOffset; uint _mvh_pad; mat4 _flw_modelMatrixUniform; mat3 _flw_normalMatrixUniform; };\n";
        int headerEnd=src.indexOf('\n');src=src.substring(0,headerEnd+1)+draw+src.substring(headerEnd+1);
        src=adaptUniforms(src);
        src=src.replaceAll("layout\\(std430, binding = 1\\)","layout(std430,set=1,binding=0)")
            .replaceAll("layout\\(std430, binding = _FLW_LIGHT_LUT_BUFFER_BINDING\\)","layout(std430,set=1,binding=2)")
            .replaceAll("layout\\(std430, binding = _FLW_LIGHT_SECTIONS_BUFFER_BINDING\\)","layout(std430,set=1,binding=3)");
        String[] samplers={"flw_diffuseTex","flw_overlayTex","flw_lightTex","_flw_crumblingTex"};
        for(int i=0;i<samplers.length;i++)src=src.replaceAll("uniform sampler2D "+samplers[i]+";","layout(set=2,binding="+i+") uniform sampler2D "+samplers[i]+";");
        String[] varying={"flw_vertexPos","flw_vertexColor","flw_vertexTexCoord","flw_vertexOverlay","flw_vertexLight","flw_vertexNormal","flw_distance","_flw_crumblingTexCoord","_flw_ids"};
        for(int i=0;i<varying.length;i++)src=src.replaceAll("(?m)^((?:flat )?(?:in|out) \\w+ "+varying[i]+";)","layout(location="+i+") $1");
        String[] attrs={"_flw_aPos","_flw_aColor","_flw_aTexCoord","_flw_aOverlay","_flw_aLight","_flw_aNormal"};
        for(int i=0;i<attrs.length;i++)src=src.replaceAll("(?m)^((?:in) \\w+ "+attrs[i]+";)","layout(location="+i+") $1");
        src=src.replace("out vec4 _flw_outputColor;","layout(location=0) out vec4 _flw_outputColor;");
        if(vertex){
            src=src.replace("FlwInstance instance = _flw_unpackInstance(_flw_baseInstance + gl_InstanceIndex);","FlwInstance instance = _flw_unpackInstance(_mvh_targets[gl_InstanceIndex]);");
            src=src.replace("_flw_main(instance, uint(gl_InstanceIndex), _flw_vertexOffset);","_flw_main(instance, _mvh_targets[gl_InstanceIndex], _flw_vertexOffset);");
            src=src.replace("gl_Position = flw_viewProjection * flw_vertexPos;","gl_Position = flw_viewProjection * flw_vertexPos; gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;");
            int e=src.indexOf('\n');src=src.substring(0,e+1)+"layout(std430,set=1,binding=1) readonly buffer NativeTargets { uint _mvh_targets[]; };\n"+src.substring(e+1);
        }
        return src;
    }
    private static byte[] compile(String src,boolean vertex){
        return compile(src,vertex?shaderc_vertex_shader:shaderc_fragment_shader);
    }
    private static String adaptUniforms(String src){
        String[] blocks={"_FlwFrameUniforms","_FlwFogUniforms","_FlwOptionsUniforms","_FlwPlayerUniforms","_FlwLevelUniforms"};
        for(int i=0;i<blocks.length;i++)src=src.replaceAll("layout\\s*\\([^)]*\\)\\s*uniform\\s+"+blocks[i],"layout(std140,set=0,binding="+i+") uniform "+blocks[i]);
        return src;
    }
    private static byte[] compile(String src,int kind){
        long compiler=shaderc_compiler_initialize(),options=shaderc_compile_options_initialize(),result=0;
        try{
            if(compiler==0||options==0)throw new IllegalStateException("Native Flywheel compiler allocation failed");
            shaderc_compile_options_set_target_env(options,shaderc_target_env_vulkan,shaderc_env_version_vulkan_1_2);
            shaderc_compile_options_set_optimization_level(options,shaderc_optimization_level_performance);
            result=shaderc_compile_into_spv(compiler,src,kind,"mvh/native-flywheel","main",options);
            if(result==0||shaderc_result_get_compilation_status(result)!=shaderc_compilation_status_success)throw new IllegalStateException("Native Flywheel shader kind "+kind+" compilation: "+(result==0?"no result":shaderc_result_get_error_message(result)));
            ByteBuffer b=shaderc_result_get_bytes(result);byte[] out=new byte[b.remaining()];b.get(out);return out;
        }finally{if(result!=0)shaderc_result_release(result);if(options!=0)shaderc_compile_options_release(options);if(compiler!=0)shaderc_compiler_release(compiler);}
    }
}
