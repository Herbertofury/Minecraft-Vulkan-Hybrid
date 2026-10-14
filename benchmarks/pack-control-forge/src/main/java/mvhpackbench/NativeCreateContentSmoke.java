package mvhpackbench;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.concurrent.atomic.*;

/** Original bearing/entity/embedding/disassembly and vanilla breaking entrypoints; owned diagnostic only. */
final class NativeCreateContentSmoke {
    static final JsonArray snapshots=new JsonArray();
    private static int stage;private static long phase,embeddedBefore,crumblingBefore;private static float initialAngle;
    private static final AtomicBoolean placed=new AtomicBoolean(),disassembled=new AtomicBoolean();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final BlockPos BEARING=new BlockPos(14,110,0),BREAKING=new BlockPos(5,110,0);
    static boolean tick(Minecraft mc)throws Exception {
        if(failure.get()!=null)throw new IllegalStateException("Original contraption task failed",failure.get());
        float yaw=stage>=4?30:-35;mc.player.setPos(9,114,-7);mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(yaw);mc.player.setXRot(27);mc.player.yRotO=yaw;mc.player.xRotO=27;
        if(phase==0)phase=System.nanoTime();double seconds=(System.nanoTime()-phase)/1e9;
        if(seconds>25)throw new IllegalStateException("Original content timeout at "+stage);
        if(stage==0){stage=1;phase=System.nanoTime();embeddedBefore=counter("EMBEDDED_MODEL_DRAWS");mc.getSingleplayerServer().execute(()->{try{
            var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();
            for(int x=13;x<=18;x++)for(int z=-2;z<=2;z++){level.setBlock(new BlockPos(x,108,z),Blocks.IRON_BLOCK.defaultBlockState(),3);for(int y=109;y<=115;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            // Original motor speed change automatically assembles the original bearing.
            level.setBlock(new BlockPos(14,111,0),Blocks.SLIME_BLOCK.defaultBlockState(),3);
            level.setBlock(new BlockPos(15,111,0),Blocks.OAK_PLANKS.defaultBlockState(),3);
            level.setBlock(new BlockPos(16,111,0),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            level.setBlock(new BlockPos(14,112,0),BuiltInRegistries.BLOCK.get(new ResourceLocation("create","shaft")).defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
            level.setBlock(BEARING,BuiltInRegistries.BLOCK.get(new ResourceLocation("create","mechanical_bearing")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP),3);
            level.setBlock(new BlockPos(14,109,0),BuiltInRegistries.BLOCK.get(new ResourceLocation("create","creative_motor")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP),3);
            placed.set(true);
        }catch(Throwable error){failure.set(error);}});return false;}
        if(stage==1&&placed.get()&&seconds>=8){
            Object be=mc.level.getBlockEntity(BEARING);if(be==null||!(Boolean)be.getClass().getMethod("isRunning").invoke(be))throw new IllegalStateException("Original bearing did not assemble/run");
            Object entity=be.getClass().getMethod("getMovedContraption").invoke(be);if(!(entity instanceof net.minecraft.world.entity.Entity e)||!e.isAlive())throw new IllegalStateException("Original client contraption entity missing");
            initialAngle=((Number)be.getClass().getMethod("getInterpolatedAngle",float.class).invoke(be,0f)).floatValue();
            if(counter("EMBEDDED_MODEL_DRAWS")<=embeddedBefore)throw new IllegalStateException("Original contraption has no actual native embedded draws");
            snapshot(mc,"original_rotating_bearing_contraption");NativeCreateSmoke.contentShot(mc,"original_rotating_bearing_contraption");stage=2;phase=System.nanoTime();return false;
        }
        if(stage==2&&seconds>=2){
            Object be=mc.level.getBlockEntity(BEARING);float now=((Number)be.getClass().getMethod("getInterpolatedAngle",float.class).invoke(be,0f)).floatValue();
            if(Math.abs(now-initialAngle)<.01f)throw new IllegalStateException("Original bearing animation did not advance");
            snapshot(mc,"original_contraption_rotation_advanced");NativeCreateSmoke.contentShot(mc,"original_contraption_rotation_advanced");stage=3;phase=System.nanoTime();
            mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();Object bearing=level.getBlockEntity(BEARING);bearing.getClass().getMethod("disassemble").invoke(bearing);disassembled.set(true);}catch(Throwable error){failure.set(error);}});return false;
        }
        if(stage==3&&disassembled.get()&&seconds>=5){
            Object be=mc.level.getBlockEntity(BEARING);if((Boolean)be.getClass().getMethod("isRunning").invoke(be))throw new IllegalStateException("Original bearing failed to disassemble");
            if(!mc.level.getBlockState(new BlockPos(14,111,0)).is(Blocks.SLIME_BLOCK))throw new IllegalStateException("Original contraption blocks were not restored");
            if(embeddedInstancers(mc)!=0)throw new IllegalStateException("Deleted original embedding still owns live native instancers");
            snapshot(mc,"original_contraption_disassembled_blocks_restored");NativeCreateSmoke.contentShot(mc,"original_contraption_disassembled_blocks_restored");stage=4;phase=System.nanoTime();crumblingBefore=counter("CRUMBLING_DRAWS");return false;
        }
        if(stage==4){
            mc.level.destroyBlockProgress(mc.player.getId(),BREAKING,6);
            if(seconds>=3){if(counter("CRUMBLING_DRAWS")<=crumblingBefore)throw new IllegalStateException("Vanilla breaking entrypoint did not draw original Flywheel crumbling");snapshot(mc,"original_shaft_native_breaking_overlay");NativeCreateSmoke.contentShot(mc,"original_shaft_native_breaking_overlay");stage=5;phase=System.nanoTime();}return false;
        }
        if(stage==5){mc.level.destroyBlockProgress(mc.player.getId(),BREAKING,-1);if(seconds>=2){snapshot(mc,"original_breaking_cleanup_and_intact_world");NativeCreateSmoke.contentShot(mc,"original_breaking_cleanup_and_intact_world");stage=6;}return false;}
        return stage==6;
    }
    private static long counter(String name)throws Exception{return ((AtomicLong)Class.forName("mvhflywheelbackend.NativeEngine").getField(name).get(null)).get();}
    private static Object engine(Minecraft mc)throws Exception{Object manager=Class.forName("dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl").getMethod("getOrThrow",net.minecraft.world.level.LevelAccessor.class).invoke(null,mc.level);return manager.getClass().getMethod("getEngineImpl").invoke(manager);}
    private static int embeddedInstancers(Minecraft mc)throws Exception{return ((Number)engine(mc).getClass().getMethod("nativeEmbeddedInstancerCount").invoke(engine(mc))).intValue();}
    private static void snapshot(Minecraft mc,String name)throws Exception{var q=new JsonObject();q.addProperty("phase",name);q.addProperty("embedded_instancers",embeddedInstancers(mc));for(String n:new String[]{"EMBEDDED_MODEL_DRAWS","CRUMBLING_DRAWS","MODEL_DRAWS","LIGHT_UPLOADS","LIGHT_CACHE_HITS"})q.addProperty(n,counter(n));snapshots.add(q);}
}
