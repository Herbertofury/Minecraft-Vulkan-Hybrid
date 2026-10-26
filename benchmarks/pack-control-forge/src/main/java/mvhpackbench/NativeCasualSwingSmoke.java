package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Original swing construction, rendering, mount and reload in the owned disposable native test. */
final class NativeCasualSwingSmoke {
    static final JsonArray snapshots=new JsonArray();
    static final java.util.List<String> checks=new java.util.ArrayList<>();
    private static final BlockPos TOP=new BlockPos(8,114,7),SEAT=new BlockPos(8,110,7);
    private static final AtomicBoolean placed=new AtomicBoolean(),forming=new AtomicBoolean(),formed=new AtomicBoolean(),mounted=new AtomicBoolean(),unmounted=new AtomicBoolean();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static int stage;private static long phase;private static CompletableFuture<Void> reload;
    static boolean enabled(){return net.minecraftforge.fml.ModList.get().isLoaded("casualswing");}
    static boolean tick(Minecraft mc)throws Exception {
        if(failure.get()!=null)throw new IllegalStateException("Original CasualSwing server task failed: "+failure.get(),failure.get());
        if(phase==0)phase=System.nanoTime();double seconds=(System.nanoTime()-phase)/1e9;
        if(seconds>30)throw new IllegalStateException("Original CasualSwing timeout at "+stage);
        if(!net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled())throw new IllegalStateException("Swing requires native Vulkan");
        String version=net.minecraftforge.fml.ModList.get().getModContainerById("flywheel").orElseThrow().getModInfo().getVersion().toString();
        if(!version.equals("1.0.6-beta-266"))throw new IllegalStateException("Newer Flywheel was not selected: "+version);
        if(stage!=2){mc.player.setPos(8.5,114,1.5);mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(0);mc.player.setXRot(14);mc.player.yRotO=0;mc.player.xRotO=14;}
        if(stage==0){stage=1;phase=System.nanoTime();mc.getSingleplayerServer().execute(()->{try{
            var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();
            for(int x=6;x<=10;x++)for(int z=5;z<=9;z++){level.setBlock(new BlockPos(x,109,z),Blocks.IRON_BLOCK.defaultBlockState(),3);for(int y=110;y<=115;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            var registry=Class.forName("euphy.upo.casualswing.registry.ModBlocks");
            var rope=(Block)((net.minecraftforge.registries.RegistryObject<?>)registry.getField("THICK_SWING_ROPE").get(null)).get();
            var seat=(Block)((net.minecraftforge.registries.RegistryObject<?>)registry.getField("QUARTZ_MOON_SWING_SEAT").get(null)).get();
            level.setBlock(TOP.above(),Blocks.OAK_LOG.defaultBlockState(),3);
            @SuppressWarnings("unchecked") var half=(net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.DoubleBlockHalf>)seat.getClass().getField("HALF").get(null);
            level.setBlock(SEAT,seat.defaultBlockState().setValue(half,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER),2);
            level.setBlock(SEAT.above(),seat.defaultBlockState().setValue(half,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
            for(int y=112;y<=114;y++)level.setBlock(new BlockPos(8,y,7),rope.defaultBlockState(),3);
            placed.set(true);
        }catch(Throwable e){failure.set(e);}});return false;}
        if(stage==1&&placed.get()&&seconds>=2&&forming.compareAndSet(false,true)){mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();Object be=level.getBlockEntity(TOP);if(be==null||(!(Boolean)be.getClass().getMethod("isFinalMaster").invoke(be)&&!(Boolean)be.getClass().getMethod("tryFormStructure").invoke(be)))throw new IllegalStateException("Original swing did not assemble after ticking: top="+level.getBlockState(TOP)+", seat="+level.getBlockState(SEAT)+", support="+level.getBlockState(TOP.above())+", be="+be);formed.set(true);}catch(Throwable e){failure.set(e);}});return false;}
        if(stage==1&&formed.get()&&seconds>=6){snapshot(mc,"original_casualswing_assembled_native");NativeCreateSmoke.contentShot(mc,"original_casualswing_assembled_native");checks.add("original_swing_assembly_client_sync_and_registered_renderer");checks.add("newer_flywheel_106_selected_in_actual_native_runtime");stage=2;phase=System.nanoTime();mc.getSingleplayerServer().execute(()->{try{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();
            player.setPos(8.5,111,6.5);var result=level.getBlockState(SEAT).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(8.5,111.2,7.5),Direction.UP,SEAT,false));
            if(!result.consumesAction()||player.getVehicle()==null||!player.getVehicle().getClass().getName().equals("euphy.upo.casualswing.content.entity.EntitySeat"))throw new IllegalStateException("Original interaction did not mount original seat");mounted.set(true);
        }catch(Throwable e){failure.set(e);}});return false;}
        if(stage==2&&mounted.get()&&seconds>=3){
            if(mc.player.getVehicle()==null||!mc.player.getVehicle().getClass().getName().equals("euphy.upo.casualswing.content.entity.EntitySeat"))throw new IllegalStateException("Original riding entity did not synchronize to client");
            checks.add("original_seat_interaction_mount_server_and_client");stage=3;phase=System.nanoTime();mc.getSingleplayerServer().execute(()->{try{mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).stopRiding();unmounted.set(true);}catch(Throwable e){failure.set(e);}});return false;}
        if(stage==3&&unmounted.get()&&seconds>=3){if(mc.player.isPassenger())throw new IllegalStateException("Original dismount did not synchronize");checks.add("original_seat_dismount_server_and_client");reload=mc.reloadResourcePacks();stage=4;phase=System.nanoTime();return false;}
        if(stage==4&&reload.isDone()&&seconds>=6){reload.join();snapshot(mc,"original_casualswing_after_native_reload");NativeCreateSmoke.contentShot(mc,"original_casualswing_after_native_reload");checks.add("original_swing_assets_and_registered_renderer_survive_native_resource_reload");stage=5;return false;}
        return stage==5;
    }
    private static void snapshot(Minecraft mc,String name)throws Exception {
        Object be=mc.level.getBlockEntity(TOP);if(be==null||!(Boolean)be.getClass().getMethod("isFinalMaster").invoke(be))throw new IllegalStateException("Original synchronized master missing");
        var parts=(java.util.List<?>)be.getClass().getMethod("getSwingParts").invoke(be);if(parts.size()!=5)throw new IllegalStateException("Original swing parts missing: "+parts);
        var renderer=mc.getBlockEntityRenderDispatcher().getRenderer((net.minecraft.world.level.block.entity.BlockEntity)be);
        if(renderer==null||!renderer.getClass().getName().equals("euphy.upo.casualswing.client.renderer.SwingRopeRenderer"))throw new IllegalStateException("Original swing renderer missing");
        var q=new JsonObject();q.addProperty("phase",name);q.addProperty("parts",parts.size());q.addProperty("original_renderer",renderer.getClass().getName());q.addProperty("api_renderer","VULKAN");q.addProperty("current_amplitude",be.getClass().getField("currentAmplitude").getFloat(be));q.addProperty("native_recording",net.vulkanmod.vulkan.Renderer.isRecording());snapshots.add(q);
    }
}
