package mvhpackbench;
import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.vulkanmod.gl.*;
import net.vulkanmod.vulkan.*;
import net.vulkanmod.vulkan.framebuffer.*;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.texture.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;import java.nio.*;import java.nio.file.*;import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Executes the exact original managed-state constructors, captures, copies, applies and restores in GAME. */
final class NativeEssentialManagedStateSmoke {
    private static final JsonArray controls=new JsonArray();
    private static final int[] SOURCE={90,195,15,204},DEST={165,60,240,85},EMPTY={0,0,0,0};
    private static Class<?> stateType,blendType;private static int invocations,captures,activations;
    private static void require(String name,boolean value){var row=new JsonObject();row.addProperty("control",name);row.addProperty("passed",value);controls.add(row);if(!value)throw new IllegalStateException("Original managed state: "+name);}
    private static Object invoke(Object object,String name,Class<?>[] signature,Object...args)throws Exception{invocations++;try{return object.getClass().getMethod(name,signature).invoke(object,args);}catch(InvocationTargetException error){throw new IllegalStateException("Original managed state "+name,error.getCause());}}
    private static Object get(Object object,String name)throws Exception{return invoke(object,name,new Class<?>[]{});}
    private static void set(Object object,String name,Class<?> type,Object value)throws Exception{invoke(object,name,new Class<?>[]{type},value);}
    private static Object capture()throws Exception{captures++;return get(stateType.getField("Companion").get(null),"active");}
    private static Object copy(Object object)throws Exception{invocations++;return stateType.getConstructor(stateType).newInstance(object);}
    private static void activate(Object desired,Object current,Object original,boolean force)throws Exception{activations++;invoke(desired,"activate",new Class<?>[]{stateType,stateType,boolean.class},current,original,force);}
    private static Object enumGl(Class<?> type,int gl)throws Exception{return invoke(type.getField("Companion").get(null),"fromGl",new Class<?>[]{int.class},gl);}
    private static Object blend(int equation)throws Exception{Class<?> eq=Class.forName("gg.essential.universal.shader.BlendState$Equation"),param=Class.forName("gg.essential.universal.shader.BlendState$Param");invocations++;return blendType.getConstructor(eq,param,param,param,param,boolean.class).newInstance(enumGl(eq,equation),enumGl(param,1),enumGl(param,1),enumGl(param,1),enumGl(param,1),true);}
    private static Object desired(boolean depth,boolean logic,int mode,boolean polygon,float factor,float units,Object blending,List<Boolean> mask)throws Exception{
        Object original=capture(),current=copy(original),next=copy(original);
        set(next,"setDepthTest",boolean.class,depth);set(next,"setDepthFunc",int.class,513);set(next,"setDepthMask",boolean.class,depth);set(next,"setCulling",boolean.class,false);
        set(next,"setColorLogicOp",boolean.class,logic);set(next,"setColorLogicOpMode",int.class,mode);set(next,"setBlendState",blendType,blending);set(next,"setColorMask",List.class,mask);
        set(next,"setPolygonOffset",boolean.class,polygon);set(next,"setPolygonOffsetFactor",float.class,factor);set(next,"setPolygonOffsetUnits",float.class,units);
        activate(next,current,original,false);Object observed=capture();
        require("capture-depth-test-"+activations,(boolean)get(observed,"getDepthTest")==depth);require("capture-depth-mask-"+activations,(boolean)get(observed,"getDepthMask")==depth);
        require("capture-cull-"+activations,!(boolean)get(observed,"getCulling"));require("capture-logic-enable-"+activations,(boolean)get(observed,"getColorLogicOp")==logic);
        if(logic)require("capture-logic-mode-"+activations,(int)get(observed,"getColorLogicOpMode")==mode);
        require("capture-mask-"+activations,get(observed,"getColorMask").equals(mask));require("capture-polygon-enable-"+activations,(boolean)get(observed,"getPolygonOffset")==polygon);
        if(polygon){require("capture-polygon-factor-"+activations,(float)get(observed,"getPolygonOffsetFactor")==factor);require("capture-polygon-units-"+activations,(float)get(observed,"getPolygonOffsetUnits")==units);}
        require("capture-original-blend-"+activations,get(observed,"getBlendState").equals(blending));return next;
    }
    private static void forgeProof()throws Exception{
        String[] names={"gg.essential.universal.render.ManagedGlState","gg.essential.universal.render.ManagedGlState$Companion","gg.essential.universal.shader.BlendState","gg.essential.universal.shader.BlendState$Companion"};int[] expected={1,10,4,6};int total=0;
        for(int i=0;i<names.length;i++){Class<?> type=Class.forName(names[i],false,NativeEssentialManagedStateSmoke.class.getClassLoader());require("original-game-owner-"+names[i],type.getClassLoader().getClass().getName().equals("cpw.mods.modlauncher.TransformingClassLoader")&&net.minecraftforge.fml.loading.FMLLoader.getGameLayer().modules().contains(type.getModule()));
            Path export=Minecraft.getInstance().gameDirectory.toPath().resolve(".mixin.out/class/"+names[i].replace('.','/')+".class");require("fresh-managed-export-"+names[i],Files.isRegularFile(export)&&Files.getLastModifiedTime(export).toMillis()>=ManagementFactory.getRuntimeMXBean().getStartTime());ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(export)).accept(node,0);int sites=0,gl=0;
            for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call){if(call.owner.equals(EssentialManagedStateCallers.FACADE))sites++;if(call.owner.startsWith("org/lwjgl/opengl/"))gl++;}
            require("exact-managed-transports-"+names[i],sites==expected[i]&&gl==0);total+=sites;
        }require("all21-original-managed-state-transports",total==21);
    }
    private static void pixels(JsonArray cases,VulkanImage image,String name,int[] expected)throws Exception{JsonObject row=NativeRasterStateSmoke.pixels(image,name,expected);Path root=Minecraft.getInstance().gameDirectory.toPath(),folder=root.resolve("mvh-managed-state-gpu-readback");Files.createDirectories(folder);Files.move(root.resolve("mvh-raster-state-gpu-readback/"+name+".rgba"),folder.resolve(name+".rgba"));cases.add(row);require("actual-managed-state-pixels-"+name,row.get("matched").getAsBoolean());}
    static void run(JsonArray allCases)throws Exception{
        forgeProof();stateType=Class.forName("gg.essential.universal.render.ManagedGlState");blendType=Class.forName("gg.essential.universal.shader.BlendState");Object saved=capture(),disabled=blendType.getField("DISABLED").get(null);
        Renderer renderer=Renderer.getInstance();RenderPass previous=renderer.getBoundRenderPass();var state=new NativeStencilSmoke.State();var old=PipelineState.blendInfo;var savedBlend=new PipelineState.BlendInfo(old.enabled,old.srcRgbFactor,old.dstRgbFactor,old.srcAlphaFactor,old.dstAlphaFactor,old.blendOp);savedBlend.blendOpRgb=old.blendOpRgb;savedBlend.blendOpAlpha=old.blendOpAlpha;
        int oldDepth=VRenderSystem.depthFun,oldMode=VRenderSystem.logicOpFun;boolean oldLogic=VRenderSystem.logicOp,oldClip=Renderer.isScissorEnabled();int[] box={Renderer.getScissorBox(0),Renderer.getScissorBox(1),Renderer.getScissorBox(2),Renderer.getScissorBox(3)};
        var color=VulkanImage.builder(8,8).setFormat(VK_FORMAT_R8G8B8A8_UNORM).setUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_SAMPLED_BIT).createVulkanImage();var depth=VulkanImage.builder(8,8).setFormat(VK_FORMAT_D32_SFLOAT).setUsage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT).createVulkanImage();var fb=Framebuffer.builder(color,depth).build();var builder=RenderPass.builder(fb);builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_CLEAR,VK_ATTACHMENT_STORE_OP_STORE);var pass=builder.build();int shader=NativeRasterStateSmoke.program();JsonArray cases=new JsonArray();List<Boolean> all=List.of(true,true,true,true);
        try{
            for(int i=0;i<16;i++){NativeRasterStateSmoke.start(renderer,pass,fb,shader,false,1,DEST);desired(false,true,0x1500+i,false,0,0,disabled,all);NativeRasterStateSmoke.quad(0,0);int[] expected=new int[4];for(int c=0;c<4;c++)expected[c]=NativeRasterStateSmoke.logic(i,SOURCE[c],DEST[c]);pixels(cases,color,"original-managed-logic-"+i,expected);}
            for(int i=0;i<5;i++){int equation=new int[]{0x8006,0x800A,0x800B,0x8007,0x8008}[i];NativeRasterStateSmoke.start(renderer,pass,fb,shader,false,1,DEST);desired(false,false,0x1503,false,0,0,blend(equation),all);NativeRasterStateSmoke.quad(0,0);int[] expected=new int[4];for(int c=0;c<4;c++)expected[c]=switch(i){case 0->Math.min(255,SOURCE[c]+DEST[c]);case 1->Math.max(0,SOURCE[c]-DEST[c]);case 2->Math.max(0,DEST[c]-SOURCE[c]);case 3->Math.min(SOURCE[c],DEST[c]);default->Math.max(SOURCE[c],DEST[c]);};pixels(cases,color,"original-managed-blend-equation-"+i,expected);}
            for(int i=0;i<2;i++){NativeRasterStateSmoke.start(renderer,pass,fb,shader,false,1,DEST);desired(false,false,0x1503,false,0,0,blend(0x8006),all);VRenderSystem.blendEquationSeparate(i==0?0x800A:0x8006,i==0?0x8006:0x800B);NativeRasterStateSmoke.quad(0,0);int[] expected=new int[4];for(int c=0;c<4;c++)expected[c]=c==3?(i==0?Math.min(255,SOURCE[c]+DEST[c]):Math.max(0,DEST[c]-SOURCE[c])):(i==0?Math.max(0,SOURCE[c]-DEST[c]):Math.min(255,SOURCE[c]+DEST[c]));pixels(cases,color,"native-managed-independent-RGB-alpha-equations-"+i,expected);}
            NativeRasterStateSmoke.start(renderer,pass,fb,shader,false,1,DEST);desired(false,false,0x1503,false,0,0,disabled,List.of(true,false,true,false));NativeRasterStateSmoke.quad(0,0);pixels(cases,color,"original-managed-asymmetric-color-mask",new int[]{SOURCE[0],DEST[1],SOURCE[2],DEST[3]});
            NativeRasterStateSmoke.start(renderer,pass,fb,shader,false,.5f,EMPTY);VRenderSystem.disablePolygonOffset();VRenderSystem.polygonOffset(0,0);desired(true,false,0x1503,true,-4,-4,disabled,all);NativeRasterStateSmoke.quad(0,0);pixels(cases,color,"original-managed-enable-before-offset-parameters",SOURCE);
            NativeRasterStateSmoke.start(renderer,pass,fb,shader,false,.5f,EMPTY);desired(true,false,0x1503,true,4,4,disabled,all);NativeRasterStateSmoke.quad(0,0);pixels(cases,color,"original-managed-active-offset-update",EMPTY);
            NativeRasterStateSmoke.start(renderer,pass,fb,shader,false,.5f,EMPTY);desired(true,false,0x1503,false,4,4,disabled,all);NativeRasterStateSmoke.quad(0,0);pixels(cases,color,"original-managed-polygon-disable",EMPTY);
            ByteBuffer query=ByteBuffer.allocateDirect(9);for(int i=0;i<9;i++)query.put(i,(byte)77);query.position(2).limit(7);NativeEssentialStateApi.glGetBooleanv(3107,query);require("managed-query-cursor-limit-sentinels",query.position()==2&&query.limit()==7&&query.get(0)==77&&query.get(1)==77&&query.get(6)==77);
            for(int parameter:new int[]{0,0x0D33}){try{NativeEssentialStateApi.glGetInteger(parameter);throw new AssertionError("Unknown state query accepted");}catch(IllegalArgumentException expected){require("unknown-state-query-no-fabricated-capability-"+parameter,true);}}
            int unchangedMode=VRenderSystem.logicOpFun;try{NativeEssentialStateApi._logicOp(1234);throw new AssertionError("Unknown logic op accepted");}catch(IllegalArgumentException expected){require("invalid-logic-op-preserves-state",VRenderSystem.logicOpFun==unchangedMode);}
            require("27-managed-and-independent-equation-GPU-cases",cases.size()==27);
        }finally{activate(saved,capture(),saved,true);GlProgram.useProgram(0);GlProgram.deleteProgram(shader);state.restore();PipelineState.blendInfo=savedBlend;VRenderSystem.depthFun=oldDepth;VRenderSystem.logicOpFun=oldMode;VRenderSystem.logicOp=oldLogic;renderer.beginRendering(previous,previous.getFramebuffer());Renderer.setInvertedViewport(0,0,previous.getFramebuffer().getWidth(),previous.getFramebuffer().getHeight());Renderer.setScissor(box[0],box[1],box[2],box[3]);Renderer.setScissorEnabled(oldClip);renderer.invalidateRenderState();fb.cleanUp(false);pass.cleanUp();color.free();depth.free();}
        require("managed-native-error-queue-clean",NativeGlErrors.consume()==0);for(var row:cases)allCases.add(row);var result=new JsonObject();result.addProperty("accepted",true);result.addProperty("automatic_original_GAME_methods_executed",true);result.addProperty("original_classes",4);result.addProperty("native_state_call_sites",21);result.addProperty("original_method_invocations",invocations);result.addProperty("original_state_captures",captures);result.addProperty("original_state_activations",activations);result.addProperty("GPU_cases",27);result.addProperty("original_managed_GPU_cases",25);result.addProperty("independent_native_separate_equation_cases",2);result.addProperty("private_loader_only",false);result.addProperty("full_typed_provider_or_FPS_accepted",false);result.add("controls",controls);Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-managed-state-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
}
