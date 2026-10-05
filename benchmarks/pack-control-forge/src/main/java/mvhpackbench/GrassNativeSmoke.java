package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Runs only in the marker-verified disposable cumulative world. No FPS is accepted here. */
final class GrassNativeSmoke {
    static boolean enabled(){return Boolean.getBoolean("mvh.pack.grass.smoke");}
    private static long started,phaseStarted;
    private static int step;
    private static float phase;
    private static Integer originalFov;
    private static long capturesBeforeFov;
    private static final JsonObject fovEvidence=new JsonObject();
    private static boolean written;
    private static String renderer="UNKNOWN";
    private static CompletableFuture<Void> reload;
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final AtomicReference<double[]> ground=new AtomicReference<>();
    private static final List<String> checks=Collections.synchronizedList(new ArrayList<>());
    private static int peakMeshes,peakTrailCells;
    private static final JsonArray cacheSnapshots=new JsonArray();

    static void tick(Minecraft mc) {
        if(written)return;
        if(started==0){if(mc.level==null||mc.player==null)return;verify(mc);started=phaseStarted=System.nanoTime();}
        if(System.nanoTime()-started>150_000_000_000L){finish(mc,new IllegalStateException("Grass smoke timed out at step "+step));return;}
        if(failure.get()!=null){finish(mc,failure.get());return;}
        if(mc.level==null||mc.player==null)return;
        if(mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.releaseMouse();
        try {
            boolean nativeApi=(Boolean)Class.forName("net.vulkanmod.compat.UniversalRendererGate").getMethod("vulkanRendererEnabled").invoke(null);
            renderer=nativeApi?"VULKAN":"OPENGL_FALLBACK";if(!nativeApi)throw new IllegalStateException("Native renderer required");
            if(mc.screen!=null&&step!=1)return;
            double seconds=(System.nanoTime()-phaseStarted)/1e9;
            if(step==0&&seconds>=2){snapshot(mc,"before_reload");reload=mc.reloadResourcePacks();step=1;return;}
            if(step==1&&reload.isDone()){
                reload.join();checks.add("native_client_resource_reload");phaseStarted=System.nanoTime();step=2;
                server(mc,p->{
                    var level=p.serverLevel();
                    for(int radius=0;radius<=192;radius+=8)for(int x=-radius;x<=radius;x+=8)for(int z=-radius;z<=radius;z+=8){
                        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                        if(!level.getBlockState(new BlockPos(x,y-1,z)).is(Blocks.GRASS_BLOCK))continue;
                        double[] target={x+.5,y,z+.5};ground.set(target);p.getAbilities().flying=false;p.onUpdateAbilities();
                        p.teleportTo(level,target[0],target[1],target[2],Set.of(),0,30);return;
                    }
                    throw new IllegalStateException("No loaded grass surface found");
                });
                return;
            }
            if(step==2&&ground.get()!=null&&seconds>=10&&meshCount()>0){
                peakMeshes=meshCount();phase=animationPhase();checks.add("original_native_grass_meshes_and_shader_available");
                snapshot(mc,"grass_ground_scene");
                shot(mc);originalFov=mc.options.fov().get();capturesBeforeFov=visibilityCaptures();fovEvidence.addProperty("original_degrees",originalFov);fovEvidence.addProperty("test_degrees",110);fovEvidence.addProperty("captures_before",capturesBeforeFov);mc.options.fov().set(110);phaseStarted=System.nanoTime();step=3;
                return;
            }
            if(step==3&&seconds>=4){
                float next=animationPhase();if(!Float.isFinite(next)||Float.floatToRawIntBits(next)==Float.floatToRawIntBits(phase))throw new IllegalStateException("Animation phase did not advance");
                checks.add("original_animation_phase_advanced");shot(mc);mc.options.keyUp.setDown(true);phaseStarted=System.nanoTime();step=4;
                long capturesAfterFov=visibilityCaptures();fovEvidence.addProperty("captures_after",capturesAfterFov);
                if(capturesAfterFov<=capturesBeforeFov)throw new IllegalStateException("Stationary FOV change did not refresh native visibility");
                checks.add("native_visibility_updated_after_stationary_fov_change");mc.options.fov().set(originalFov);
                return;
            }
            if(step==4){
                peakTrailCells=Math.max(peakTrailCells,trailCells());
                if(seconds>=8){
                    mc.options.keyUp.setDown(false);shot(mc);
                    if(peakTrailCells<=0)throw new IllegalStateException("No original trail cells stamped during movement");
                    checks.add("client_movement_stamped_original_trail_field");step=5;phaseStarted=System.nanoTime();
                    server(mc,p->{p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(Objects.requireNonNull(p.server.getLevel(Level.NETHER)),.5,110.5,.5,Set.of(),-68.7007f,9.999512f);});
                    return;
                }
            }
            if(step==5&&mc.level.dimension().equals(Level.NETHER)&&seconds>=3){
                checks.add("client_entered_native_nether");shot(mc);step=6;phaseStarted=System.nanoTime();
                server(mc,p->p.teleportTo(Objects.requireNonNull(p.server.getLevel(Level.OVERWORLD)),.5,110.5,.5,Set.of(),-68.7007f,9.999512f));
                return;
            }
            if(step==6&&mc.level.dimension().equals(Level.OVERWORLD)&&seconds>=12&&meshCount()>0){
                checks.add("native_overworld_grass_rebuilt_after_roundtrip");shot(mc);step=7;finish(mc,null);
            }
        } catch(Throwable error){finish(mc,error);}
    }
    private static void verify(Minecraft mc){
        try {
            var file=mc.gameDirectory.toPath().resolve("MVH-CUMULATIVE-IDENTITY.json");
            var marker=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(!marker.get("task").getAsString().equals("mvh-cumulative-20261005")||!marker.get("id").getAsString().equals("local:b6985be9-ded6-4a96-85cd-5a2548b1d900")||mc.getSingleplayerServer()==null)throw new IllegalStateException("Unowned grass smoke world");
        }catch(Exception e){throw new IllegalStateException("Grass smoke ownership unavailable",e);}
    }
    private static int meshCount() throws Exception {
        var type=Class.forName("com.leonardoinc22.shortgrass.client.render.GrassSectionCache");var method=type.getDeclaredMethod("meshes");method.setAccessible(true);
        var shaderType=Class.forName("com.leonardoinc22.shortgrass.client.render.GrassRenderType");var shader=shaderType.getDeclaredMethod("getGrassShader");shader.setAccessible(true);
        if(shader.invoke(null)==null)return 0;
        return ((Map<?,?>)method.invoke(null)).size();
    }
    private static float animationPhase() throws Exception {
        var type=Class.forName("com.leonardoinc22.shortgrass.client.render.GrassShaderUniforms");var method=type.getDeclaredMethod("windFlutterPhase");method.setAccessible(true);return (Float)method.invoke(null);
    }
    private static int trailCells() throws Exception {
        var field=Class.forName("com.leonardoinc22.shortgrass.client.render.GrassTrailField").getDeclaredField("trailActiveCount");field.setAccessible(true);return field.getInt(null);
    }
    private static void snapshot(Minecraft mc,String scope) throws Exception {
        JsonObject state=new JsonObject();state.addProperty("scope",scope);
        var cache=Class.forName("com.leonardoinc22.shortgrass.client.render.GrassSectionCache");
        for(String name:new String[]{"DIRTY_SECTIONS","LIGHT_DIRTY_SECTIONS","RELOD_PENDING","IN_FLIGHT","TO_BUILD","TO_REFRESH","TO_RELOD"}){
            var field=cache.getDeclaredField(name);field.setAccessible(true);state.addProperty(name,((Collection<?>)field.get(null)).size());
        }
        var pos=mc.gameRenderer.getMainCamera().getPosition();state.addProperty("camera_x",pos.x);state.addProperty("camera_y",pos.y);state.addProperty("camera_z",pos.z);
        var type=Class.forName("net.vulkanmod.render.chunk.WorldRenderer");var renderer=type.getMethod("getInstance").invoke(null);
        state.addProperty("native_graph_needs_update",(Boolean)type.getMethod("graphNeedsUpdate").invoke(renderer));
        var dispatcher=type.getDeclaredField("taskDispatcher");dispatcher.setAccessible(true);var tasks=dispatcher.get(renderer);
        state.addProperty("native_terrain_tasks_idle",(Boolean)tasks.getClass().getMethod("isIdle").invoke(tasks));
        cacheSnapshots.add(state);
    }
    private static long visibilityCaptures() throws Exception {
        var type=Class.forName("net.vulkanmod.render.chunk.WorldRenderer");var renderer=type.getMethod("getInstance").invoke(null);
        var field=type.getDeclaredField("visibilityState");field.setAccessible(true);var state=field.get(renderer);
        return (Long)state.getClass().getMethod("captures").invoke(state);
    }
    private static void shot(Minecraft mc){Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});}
    private static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> action){mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure.compareAndSet(null,e);}});}
    private static void finish(Minecraft mc,Throwable error){
        if(written)return;written=true;mc.options.keyUp.setDown(false);if(originalFov!=null)mc.options.fov().set(originalFov);
        try{if(mc.level!=null&&mc.player!=null)snapshot(mc,"finish");}catch(Exception ignored){}
        JsonObject report=new JsonObject();report.addProperty("completed",step==7);report.addProperty("passed",step==7&&error==null);report.addProperty("api_renderer",renderer);report.addProperty("step",step);report.addProperty("error_type",error==null?"":error.getClass().getName());report.addProperty("peak_original_meshes",peakMeshes);report.addProperty("peak_original_active_trail_cells",peakTrailCells);report.addProperty("scope","Resource reload, original animation state, real client movement/trail state and dimension roundtrip; screenshots require visual review; no FPS or all-style/shader-provider parity acceptance");
        JsonArray passed=new JsonArray();synchronized(checks){checks.forEach(passed::add);}report.add("checks",passed);
        report.add("cache_snapshots",cacheSnapshots);
        report.add("fov_evidence",fovEvidence);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("mvh-grass-native-smoke.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException("Unable to retain grass smoke report",e);}
        if(error!=null)error.printStackTrace();System.out.println("[MVH Grass Smoke] "+report);mc.stop();
    }
}
