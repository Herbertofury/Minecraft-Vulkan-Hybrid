package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** End-to-end original Create visuals and Ponder UI in the exact task-owned disposable world. */
final class NativeCreateSmoke {
    private static Boolean enabled;
    private static long start,phase,drawsBefore,enginesBefore,registrationsBefore;
    private static long[] stencilBefore;
    private static long blitsBefore;
    private static int requestedSeek;
    private static boolean shotPending;
    private static String shotLabel;
    private static Runnable afterShot;
    private static int step;
    private static boolean written;
    private static CompletableFuture<Void> reload;
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final List<String> checks=Collections.synchronizedList(new ArrayList<>());
    private static final JsonArray snapshots=new JsonArray();
    private static final AtomicBoolean machinery=new AtomicBoolean();
    static boolean enabled(){
        if(enabled!=null)return enabled;
        var root=Minecraft.getInstance().gameDirectory.toPath();var request=root.resolve("mvh-create-native-request.json");
        if(!Files.exists(request))return enabled=false;
        try{var q=JsonParser.parseString(Files.readString(request)).getAsJsonObject();
            var marker=JsonParser.parseString(Files.readString(root.resolve("MVH-CUMULATIVE-IDENTITY.json"))).getAsJsonObject();
            if(q.size()!=1||!q.get("task").getAsString().equals("mvh-native-create-engine-20261005")||!marker.get("task").getAsString().equals("mvh-cumulative-20261005")||!marker.get("id").getAsString().equals("local:b6985be9-ded6-4a96-85cd-5a2548b1d900")||root.getFileName().toString().equalsIgnoreCase("Noxviola"))throw new IllegalStateException("Unowned Create smoke request");
            return enabled=true;
        }catch(Exception e){throw new IllegalStateException("Invalid native Create smoke request",e);}
    }
    static void tick(Minecraft mc){
        if(written)return;if(mc.level==null||mc.player==null)return;
        if(start==0){if(mc.getSingleplayerServer()==null)throw new IllegalStateException("Create smoke requires isolated integrated server");start=phase=System.nanoTime();}
        if(failure.get()!=null){finish(mc,failure.get());return;}
        if(System.nanoTime()-start>240_000_000_000L){finish(mc,new IllegalStateException("Create native smoke timeout at "+step));return;}
        try{
            if(!(Boolean)Class.forName("net.vulkanmod.compat.UniversalRendererGate").getMethod("vulkanRendererEnabled").invoke(null))throw new IllegalStateException("Original Create test selected OpenGL fallback");
            if(mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.releaseMouse();
            if(shotPending)return;
            if(step<5||step>=8){if(mc.screen!=null)return;float yaw=step==9?90:-90,pitch=step==9?0:16;mc.player.setPos(.5,112,.5);mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;}
            double seconds=(System.nanoTime()-phase)/1e9;
            if(step==0&&seconds>=3){step=1;phase=System.nanoTime();mc.getSingleplayerServer().execute(()->{try{
                var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();
                for(int x=2;x<=12;x++)for(int z=-3;z<=5;z++){level.setBlock(new BlockPos(x,109,z),Blocks.IRON_BLOCK.defaultBlockState(),3);for(int y=110;y<=113;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
                for(int z:new int[]{0,3}){
                    var motor=BuiltInRegistries.BLOCK.get(new ResourceLocation("create","creative_motor")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST);
                    level.setBlock(new BlockPos(3,110,z),motor,3);
                    for(int x=4;x<=8;x++)level.setBlock(new BlockPos(x,110,z),BuiltInRegistries.BLOCK.get(new ResourceLocation("create","shaft")).defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
                    level.setBlock(new BlockPos(9,110,z),BuiltInRegistries.BLOCK.get(new ResourceLocation("create","encased_fan")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST),3);
                }
                machinery.set(true);
            }catch(Throwable e){failure.set(e);}});return;}
            if(step==1&&machinery.get()&&seconds>=10){
                snapshot(mc,"original_powered_machinery");shot(mc);requireBackend();drawsBefore=counter("MODEL_DRAWS");
                mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();
                    for(int z:new int[]{0,3})for(int x:new int[]{3,5,9}){var be=level.getBlockEntity(new BlockPos(x,110,z));if(be==null||!be.getClass().getName().startsWith("com.simibubi.create."))throw new IllegalStateException("Original kinetic BE missing");float speed=((Number)be.getClass().getMethod("getSpeed").invoke(be)).floatValue();if(!Float.isFinite(speed)||speed==0)throw new IllegalStateException("Original chain not powered at "+x+","+z);}
                    checks.add("original_motor_shafts_fans_powered_on_server");
                    level.setBlock(new BlockPos(7,110,-2),Blocks.GLOWSTONE.defaultBlockState(),3);
                }catch(Throwable e){failure.set(e);}});
                step=2;phase=System.nanoTime();return;
            }
            if(step==2&&seconds>=5){if(counter("MODEL_DRAWS")<=drawsBefore)throw new IllegalStateException("No original model draws advanced");snapshot(mc,"machinery_after_light_update");shot(mc);if(counter("LIGHT_CACHE_HITS")==0||counter("LIGHT_UPLOADS")==0)throw new IllegalStateException("Native frame-fenced original lighting snapshots are missing");checks.add("original_light_revision_snapshots_reused_after_frame_fence");checks.add("native_original_models_draw_over_multiple_frames");enginesBefore=counter("ENGINES");registrationsBefore=counter("CHUNK_VISUAL_REGISTRATIONS");reload=mc.reloadResourcePacks();step=3;return;}
            if(step==3&&reload.isDone()){reload.join();drawsBefore=counter("MODEL_DRAWS");step=4;phase=System.nanoTime();return;}
            if(step==4&&seconds>=8){requireBackend();snapshot(mc,"machinery_after_resource_reload");shot(mc);
                if(counter("MODEL_DRAWS")<=drawsBefore||counter("ENGINES")<=enginesBefore||counter("CHUNK_VISUAL_REGISTRATIONS")<=registrationsBefore||instancers(mc)==0)throw new IllegalStateException("Original machinery lost its native visuals after resource reload");
                checks.add("original_resource_reload_re_registered_and_rebuilt_native_machinery");
                afterShot=()->{try{Class<?> type=Class.forName("net.createmod.ponder.foundation.ui.PonderUI");Object screen=type.getMethod("of",ResourceLocation.class).invoke(null,new ResourceLocation("create","shaft"));
                    if(!(screen instanceof Screen s))throw new IllegalStateException("Original registered shaft Ponder missing");mc.setScreen(s);step=5;phase=System.nanoTime();}catch(Throwable e){failure.set(e);}};return;}
            if(step==5&&seconds>=12){if(!mc.screen.getClass().getName().equals("net.createmod.ponder.foundation.ui.PonderUI"))throw new IllegalStateException("Original Ponder UI closed unexpectedly");snapshot(mc,"original_ponder_shaft_ui");shot(mc);
                if(sceneTime(mc)<=0)throw new IllegalStateException("Original Ponder story did not advance");
                requestedSeek=Math.min(40,Math.max(1,sceneTime(mc)/2));mc.screen.getClass().getMethod("seekToTime",int.class).invoke(mc.screen,requestedSeek);snapshot(mc,"original_ponder_immediately_after_seek");if(sceneTime(mc)<requestedSeek)throw new IllegalStateException("Original Ponder seek did not reach a valid story time");step=6;phase=System.nanoTime();return;}
            if(step==6&&seconds>=5){snapshot(mc,"original_ponder_after_seek");shot(mc);checks.add("original_registered_ponder_story_and_original_replay_seek_native_rendering");
                afterShot=()->{try{stencilBefore=stencilCounts();blitsBefore=colorBlits();var factory=net.minecraftforge.fml.ModList.get().getModContainerById("create").orElseThrow().getCustomExtension(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class).orElseThrow();
                    Screen config=factory.screenFunction().apply(mc,mc.screen);if(config==null||!config.getClass().getName().startsWith("net.createmod.catnip.config.ui."))throw new IllegalStateException("Original registered Create config screen missing");mc.setScreen(config);step=7;phase=System.nanoTime();}catch(Throwable e){failure.set(e);}};return;}
            if(step==7&&seconds>=5){snapshot(mc,"original_registered_create_config_ui");shot(mc);long[] counts=stencilCounts();long enables=counts[0]-stencilBefore[0],disables=counts[1]-stencilBefore[1];
                if(enables<=0||enables!=disables||stencilEnabled())throw new IllegalStateException("Original Ponder native clipping calls missing, unbalanced or leaked");
                if(colorBlits()<=blitsBefore)throw new IllegalStateException("Original offscreen Create config UI was never copied by actual Vulkan image blits");
                checks.add("original_registered_create_config_ui_actual_native_color_blits");checks.add("original_registered_create_config_ui_balanced_native_stencil_clipping");afterShot=()->{try{drawsBefore=counter("MODEL_DRAWS");mc.setScreen(null);step=8;phase=System.nanoTime();}catch(Throwable e){failure.set(e);}};return;}
            if(step==8&&seconds>=5){requireBackend();snapshot(mc,"world_after_original_ui_close");shot(mc);if(stencilEnabled()||counter("MODEL_DRAWS")<=drawsBefore||counter("GPU_CULL_DISPATCHES")==0||counter("GPU_APPLY_DISPATCHES")==0||counter("GPU_LAST_VISIBLE")==0)throw new IllegalStateException("Original UI did not restore actual native compute/drawing and stencil state");checks.add("original_uis_close_and_restore_native_compute_world");afterShot=()->{step=9;phase=System.nanoTime();};return;}
            if(step==9&&seconds>=2){snapshot(mc,"machinery_behind_camera_gpu_culled");shot(mc);if(counter("GPU_LAST_SUBMITTED")==0||counter("GPU_LAST_VISIBLE")!=0)throw new IllegalStateException("Actual native GPU frustum cull did not reject machinery behind the camera");checks.add("original_instance_writers_and_model_bounds_gpu_cull_offscreen_machinery");afterShot=()->{step=10;phase=System.nanoTime();};return;}
            if(step==10&&seconds>=2){snapshot(mc,"machinery_gpu_visibility_restored");shot(mc);if(counter("GPU_LAST_VISIBLE")==0||stencilEnabled())throw new IllegalStateException("Original native machinery did not return after camera restoration");checks.add("original_machinery_native_indirect_visibility_restored_after_camera_turn");afterShot=()->{step=11;phase=System.nanoTime();};}
            if(step==11&&NativeCreateContentSmoke.tick(mc)){checks.add("original_rotating_bearing_contraption_native_embedding_and_animation");checks.add("original_contraption_disassembly_restores_blocks_and_retires_empty_embedding");checks.add("original_vanilla_breaking_entrypoint_native_crumbling_and_cleanup");step=12;finish(mc,null);}
        }catch(Throwable e){finish(mc,e);}
    }
    private static long colorBlits()throws Exception{return ((AtomicLong)Class.forName("net.vulkanmod.gl.GlFramebufferBlit").getField("COLOR_BLITS").get(null)).get();}
    private static long counter(String name)throws Exception{return ((AtomicLong)Class.forName("mvhflywheelbackend.NativeEngine").getField(name).get(null)).get();}
    private static int instancers(Minecraft mc)throws Exception{var manager=Class.forName("dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl").getMethod("getOrThrow",net.minecraft.world.level.LevelAccessor.class).invoke(null,mc.level);var engine=manager.getClass().getMethod("getEngineImpl").invoke(manager);return ((Number)engine.getClass().getMethod("nativeInstancerCount").invoke(engine)).intValue();}
    private static long[] stencilCounts()throws Exception{return (long[])Class.forName("mvhpondercompat.NativeStencilBridge").getMethod("callCounts").invoke(null);}
    private static boolean stencilEnabled()throws Exception{return (Boolean)Class.forName("net.vulkanmod.vulkan.VRenderSystem").getField("stencilTest").get(null);}
    private static int sceneTime(Minecraft mc)throws Exception{var scene=mc.screen.getClass().getMethod("getActiveScene").invoke(mc.screen);return ((Number)scene.getClass().getMethod("getCurrentTime").invoke(scene)).intValue();}
    private static void requireBackend()throws Exception{String name=(String)Class.forName("dev.engine_room.flywheel.impl.BackendManagerImpl").getMethod("getBackendString").invoke(null);if(!name.equals("mvh:native_vulkan"))throw new IllegalStateException("Flywheel backend "+name);if(counter("MODEL_DRAWS")==0)throw new IllegalStateException("Native world rendered no original models");}
    private static void snapshot(Minecraft mc,String label)throws Exception{JsonObject q=new JsonObject();q.addProperty("phase",label);q.addProperty("screen",mc.screen==null?"none":mc.screen.getClass().getName());for(String n:new String[]{"ENGINES","WORLD_FRAMES","MODEL_DRAWS","CRUMBLING_DRAWS","CHUNK_VISUAL_REGISTRATIONS","RELOAD_EVENTS","GPU_CULL_DISPATCHES","GPU_APPLY_DISPATCHES","GPU_LAST_VISIBLE","GPU_LAST_SUBMITTED","LIGHT_UPLOADS","LIGHT_UPLOAD_BYTES","LIGHT_CACHE_HITS"})q.addProperty(n,counter(n));q.addProperty("native_color_blits",colorBlits());q.addProperty("main_framebuffer_name",mc.getMainRenderTarget().frameBufferId);q.addProperty("main_name_aliases_native_main",net.vulkanmod.gl.GlFramebuffer.isMainFramebuffer(mc.getMainRenderTarget().frameBufferId));q.addProperty("world_instancers",instancers(mc));long[] stencil=stencilCounts();q.addProperty("stencil_enables",stencil[0]);q.addProperty("stencil_disables",stencil[1]);q.addProperty("stencil_enabled",stencilEnabled());if(mc.screen!=null&&mc.screen.getClass().getName().equals("net.createmod.ponder.foundation.ui.PonderUI")){q.addProperty("original_scene_time",sceneTime(mc));q.addProperty("ponder_renderer","Original Ponder SchematicLevel through Hari native vanilla pipelines; original upstream level is not a Flywheel VisualizationLevel.");}q.addProperty("backend",(String)Class.forName("dev.engine_room.flywheel.impl.BackendManagerImpl").getMethod("getBackendString").invoke(null));snapshots.add(q);}
    static void contentShot(Minecraft mc,String label)throws Exception{snapshot(mc,label);shot(mc);}
    private static void shot(Minecraft mc){shotPending=true;shotLabel=snapshots.get(snapshots.size()-1).getAsJsonObject().get("phase").getAsString();}
    static void guiPost(net.minecraftforge.client.event.RenderGuiEvent.Post event){Minecraft mc=Minecraft.getInstance();if(enabled()&&mc.screen==null)captureInFrame(mc);}
    static void screenPost(net.minecraftforge.client.event.ScreenEvent.Render.Post event){Minecraft mc=Minecraft.getInstance();if(enabled()&&mc.screen!=null&&event.getScreen()==mc.screen)captureInFrame(mc);}
    static void renderEnd(Minecraft mc){/* Native screenshots must finish before the image is presented. */}
    private static void captureInFrame(Minecraft mc){if(!shotPending||written)return;try{
        if(!net.vulkanmod.vulkan.Renderer.isRecording()||net.vulkanmod.vulkan.Renderer.getInstance().getBoundRenderPass()==null)throw new IllegalStateException("Original screenshot requires active native recording before present");
        if(shotLabel.equals("original_registered_create_config_ui"))NativeFramebufferBlitSmoke.captureOriginalOffscreen(mc);
        if(shotLabel.equals("world_after_original_ui_close")){NativeFramebufferBlitSmoke.run();checks.add("six_actual_native_registry_framebuffer_blit_gpu_pixel_controls");}
        Screenshot.grab(mc.gameDirectory,"mvh-create-"+shotLabel+".png",mc.getMainRenderTarget(),x->{});shotPending=false;Runnable next=afterShot;afterShot=null;if(next!=null)next.run();
    }catch(Throwable error){shotPending=false;failure.set(error);}}
    private static void finish(Minecraft mc,Throwable e){written=true;if(e!=null)failure.compareAndSet(null,e);JsonObject q=new JsonObject();q.addProperty("completed",step==12);q.addProperty("passed",step==12&&failure.get()==null);q.addProperty("step",step);q.addProperty("requested_story_seek",requestedSeek);q.addProperty("api_renderer","VULKAN");q.addProperty("fps_accepted",false);q.addProperty("error",failure.get()==null?"":failure.get().toString());JsonArray a=new JsonArray();checks.forEach(a::add);q.add("checks",a);q.add("snapshots",snapshots);q.add("gpu_framebuffer_blit_cases",NativeFramebufferBlitSmoke.cases);q.add("original_content_snapshots",NativeCreateContentSmoke.snapshots);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("mvh-create-native-smoke.json"),new GsonBuilder().setPrettyPrinting().create().toJson(q));}catch(java.io.IOException io){throw new java.io.UncheckedIOException(io);}System.out.println("[MVH Create] "+q);mc.stop();}
}
