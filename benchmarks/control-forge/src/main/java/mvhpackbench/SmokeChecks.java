package mvhpackbench;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.*;

/** Destructive actions affect only the launcher's task-owned disposable benchmark world. */
final class SmokeChecks {
    private static long start;
    private static int step;
    private static CompletableFuture<Void> reload;
    private static volatile Entity octopus;
    private static final List<String> passed=Collections.synchronizedList(new ArrayList<>());
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    static boolean enabled(){return Boolean.getBoolean("mvh.pack.smoke");}
    static void tick(Minecraft mc) {
        if(!enabled())return;
        if(!mc.gameDirectory.getName().equals("noxviola-candidate")||mc.getSingleplayerServer()==null)throw new IllegalStateException("Smoke checks require the task candidate");
        if(start==0)start=System.nanoTime();long seconds=(System.nanoTime()-start)/1_000_000_000L;
        if(step==0&&seconds>=2){step++;reload=mc.reloadResourcePacks();}
        if(step==1&&reload!=null&&reload.isDone()) {try{reload.join();passed.add("client_resource_reload");}catch(Throwable e){failure.compareAndSet(null,e);}step++;}
        if(step==2&&seconds>=12){step++;onServer(mc,player->{
            EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation("hybrid_aquatic","octopus")).orElseThrow();
            Entity entity=type.create(player.serverLevel());if(entity==null)throw new IllegalStateException("Octopus creation failed");
            entity.moveTo(.5,109.5,.5,0,0);if(!player.serverLevel().addFreshEntity(entity))throw new IllegalStateException("Octopus spawn failed");octopus=entity;passed.add("modded_octopus_spawn");
        });}
        if(step==3&&seconds>=18&&octopus!=null){step++;onServer(mc,player->{octopus.kill();passed.add("physics_geckolib_octopus_death");});}
        if(step==4&&seconds>=24){step++;onServer(mc,player->{player.teleportTo(Objects.requireNonNull(player.server.getLevel(Level.NETHER)),.5,110.5,.5,Set.of(),-68.7007f,9.999512f);passed.add("server_teleport_nether");});}
        if(step==5&&seconds>=36&&mc.level.dimension().equals(Level.NETHER)){step++;passed.add("client_entered_nether");onServer(mc,player->{player.teleportTo(Objects.requireNonNull(player.server.getLevel(Level.OVERWORLD)),.5,110.5,.5,Set.of(),-68.7007f,9.999512f);passed.add("server_teleport_overworld");});}
        if(step==6&&mc.level.dimension().equals(Level.OVERWORLD)){step++;passed.add("client_returned_overworld");write(mc);}
        if(seconds>=120&&step<7){failure.compareAndSet(null,new IllegalStateException("Native smoke checks timed out at step "+step));write(mc);mc.stop();}
    }
    private static void onServer(Minecraft mc,java.util.function.Consumer<ServerPlayer> action) {
        var server=mc.getSingleplayerServer();server.execute(()->{try{action.accept(server.getPlayerList().getPlayers().get(0));}catch(Throwable e){failure.compareAndSet(null,e);}});
    }
    private static void write(Minecraft mc) {
        var result=new com.google.gson.JsonObject();result.addProperty("completed",step==7);result.addProperty("passed",step==7&&failure.get()==null);result.addProperty("error_type",failure.get()==null?"":failure.get().getClass().getName());result.addProperty("step",step);var checks=new com.google.gson.JsonArray();synchronized(passed){passed.forEach(checks::add);}result.add("checks",checks);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("mvh-native-smoke.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        System.out.println("[MVH Smoke] "+result);
    }
}
