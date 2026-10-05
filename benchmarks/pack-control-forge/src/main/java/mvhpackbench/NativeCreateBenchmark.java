package mvhpackbench;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.nio.file.Files;
import java.util.concurrent.atomic.*;

/** Matched original powered Create scene; full recorder warmup starts after fixture verification. */
final class NativeCreateBenchmark {
    static final String CAMERA="0.5,112,0.5 / yaw -90 / pitch 16 / original powered Create machinery";
    private static Boolean enabled;private static boolean requested,verifyRequested;private static long placedAt;
    private static final AtomicBoolean placed=new AtomicBoolean(),verified=new AtomicBoolean();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    static boolean enabled(){
        if(enabled!=null)return enabled;
        var root=Minecraft.getInstance().gameDirectory.toPath();var request=root.resolve("mvh-create-machinery-benchmark-request.json");
        if(!Files.exists(request))return enabled=false;
        try {
            var q=JsonParser.parseString(Files.readString(request)).getAsJsonObject();var identity=JsonParser.parseString(Files.readString(root.resolve("MVH-CUMULATIVE-IDENTITY.json"))).getAsJsonObject();
            if(q.size()!=1||!q.get("task").getAsString().equals("mvh-create-machinery-matched-20261005")||identity.size()!=3||!identity.get("task").getAsString().equals("mvh-cumulative-20261005")||!identity.get("id").getAsString().equals("local:b6985be9-ded6-4a96-85cd-5a2548b1d900")||!identity.get("source").getAsString().equals("local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3"))throw new IllegalStateException("Create machinery benchmark requires exact task ownership");
            return enabled=true;
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    static boolean ready(Minecraft mc){
        if(failure.get()!=null)throw new IllegalStateException("Original Create benchmark fixture failed",failure.get());
        if(verified.get())return true;
        if(!requested){requested=true;mc.getSingleplayerServer().execute(()->{try{
            var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();
            for(int x=2;x<=12;x++)for(int z=-3;z<=5;z++){level.setBlock(new BlockPos(x,109,z),Blocks.IRON_BLOCK.defaultBlockState(),3);for(int y=110;y<=113;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            for(int z:new int[]{0,3}){
                level.setBlock(new BlockPos(3,110,z),BuiltInRegistries.BLOCK.get(new ResourceLocation("create","creative_motor")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST),3);
                for(int x=4;x<=8;x++)level.setBlock(new BlockPos(x,110,z),BuiltInRegistries.BLOCK.get(new ResourceLocation("create","shaft")).defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),3);
                level.setBlock(new BlockPos(9,110,z),BuiltInRegistries.BLOCK.get(new ResourceLocation("create","encased_fan")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST),3);
            }
            level.setBlock(new BlockPos(7,110,-2),Blocks.GLOWSTONE.defaultBlockState(),3);placedAt=System.nanoTime();placed.set(true);
        }catch(Throwable error){failure.set(error);}});}
        if(placed.get()&&!verifyRequested&&System.nanoTime()-placedAt>=10_000_000_000L){verifyRequested=true;mc.getSingleplayerServer().execute(()->{try{
            var level=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).serverLevel();
            for(int z:new int[]{0,3})for(int x:new int[]{3,5,9}){var be=level.getBlockEntity(new BlockPos(x,110,z));if(be==null||!be.getClass().getName().startsWith("com.simibubi.create."))throw new IllegalStateException("Original kinetic block entity missing");float speed=((Number)be.getClass().getMethod("getSpeed").invoke(be)).floatValue();if(!Float.isFinite(speed)||speed==0)throw new IllegalStateException("Original kinetic chain not powered");}
            String backend=(String)Class.forName("dev.engine_room.flywheel.impl.BackendManagerImpl").getMethod("getBackendString").invoke(null);if(!backend.equals("mvh:native_vulkan"))throw new IllegalStateException("Unexpected Flywheel backend "+backend);
            if(((AtomicLong)Class.forName("mvhflywheelbackend.NativeEngine").getField("MODEL_DRAWS").get(null)).get()==0)throw new IllegalStateException("Original Create benchmark has no actual native model draws");
            verified.set(true);
        }catch(Throwable error){failure.set(error);}});}
        return verified.get();
    }
    static com.google.gson.JsonObject counters(){
        var q=new com.google.gson.JsonObject();
        for(String name:new String[]{"WORLD_FRAMES","MODEL_DRAWS","GPU_CULL_DISPATCHES","GPU_APPLY_DISPATCHES","GPU_LAST_VISIBLE","GPU_LAST_SUBMITTED","LIGHT_UPLOADS","LIGHT_UPLOAD_BYTES","LIGHT_CACHE_HITS","DESCRIPTOR_UPDATE_CALLS","DESCRIPTORS_WRITTEN"})try{
            q.addProperty(name,((AtomicLong)Class.forName("mvhflywheelbackend.NativeEngine").getField(name).get(null)).get());
        }catch(NoSuchFieldException absent){q.add(name,com.google.gson.JsonNull.INSTANCE);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot report original native machinery counters",failure);}
        for(String name:new String[]{"CACHE_HITS","CACHE_MISSES","STATE_CAPTURES","STATE_CHANGES"})try{q.addProperty("GRASS_FRUSTUM_"+name,Class.forName("mvhgrasscompat.ExactFrustumCache").getField(name).getLong(null));}catch(ClassNotFoundException|NoSuchFieldException absent){q.add("GRASS_FRUSTUM_"+name,com.google.gson.JsonNull.INSTANCE);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot report grass visibility counters",failure);}
        return q;
    }
    static void camera(Minecraft mc){mc.player.setPos(.5,112,.5);mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(-90);mc.player.setXRot(16);mc.player.yRotO=-90;mc.player.xRotO=16;}
}
