package mvhpackbench;

import com.google.gson.*;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.*;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Fixed disposable-world chunk purpose measurement; no FPS acceptance. */
final class ChunkPurpose {
    private static final Path request=Minecraft.getInstance().gameDirectory.toPath().resolve("mvh-chunk-purpose-request.json");
    private static final boolean enabled=Files.isRegularFile(request);
    private static final int origin=1024,side=12,total=side*side;
    private static final long deadline=System.nanoTime()+240_000_000_000L;
    private static final TicketType<ChunkPos> ticket=TicketType.create("mvh_chunk_purpose",Comparator.comparingLong(ChunkPos::toLong));
    private static final AtomicLong begin=new AtomicLong(),end=new AtomicLong();
    private static final AtomicInteger completed=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final List<Double> ticks=Collections.synchronizedList(new ArrayList<>());
    private static final List<String> terrain=new ArrayList<>();
    private static volatile boolean ticketsReady,verified;
    private static long joined,stop,lastTick;private static int step;
    private static List<CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>>> futures;
    static boolean enabled(){return enabled;}
    static void serverTick(TickEvent.ServerTickEvent e){
        if(!enabled||e.phase!=TickEvent.Phase.END||begin.get()==0||end.get()!=0)return;
        long now=System.nanoTime();if(lastTick!=0)ticks.add((now-lastTick)/1e6);lastTick=now;
    }
    static void tick(Minecraft mc){
        if(!enabled)return;
        try{
            if(step==0){
                JsonObject r=JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                if(r.size()!=4||!r.get("task").getAsString().equals("mvh-cumulative-chunk-purpose-20261005")||r.get("origin_x").getAsInt()!=origin||r.get("origin_z").getAsInt()!=origin||r.get("side").getAsInt()!=side)throw new IllegalStateException("Unexpected chunk request");
                JsonObject identity=JsonParser.parseString(Files.readString(mc.gameDirectory.toPath().resolve("MVH-CUMULATIVE-IDENTITY.json"))).getAsJsonObject();
                if(identity.size()!=3||!identity.get("task").getAsString().equals("mvh-cumulative-20261005")||!identity.get("id").getAsString().equals("local:b6985be9-ded6-4a96-85cd-5a2548b1d900")||!identity.get("source").getAsString().equals("local:2c68d7c5-8fae-4984-ad3e-c76db50b66c3"))throw new IllegalStateException("Chunk purpose requires the exact task-owned cumulative marker");step=1;
            }
            if(stop!=0){if(System.nanoTime()-stop>=3_000_000_000L)mc.stop();return;}
            if(System.nanoTime()>deadline)throw new IllegalStateException("Bounded chunk purpose deadline exceeded");
            if(mc.level==null||mc.player==null||mc.screen!=null)return;
            var server=Objects.requireNonNull(mc.getSingleplayerServer());
            if(!mc.level.dimension().equals(Level.OVERWORLD))throw new IllegalStateException("Chunk purpose requires Overworld");
            mc.mouseHandler.releaseMouse();mc.player.setPos(.5,110.5,.5);mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(-68.7007f);mc.player.setXRot(9.999512f);mc.player.yRotO=-68.7007f;mc.player.xRotO=9.999512f;
            if(joined==0)joined=System.nanoTime();
            if(step==1&&System.nanoTime()-joined>=20_000_000_000L){
                step=2;server.execute(()->{try{
                    if(!server.isSameThread())throw new IllegalStateException("Chunk ticket owner violation");
                    ServerLevel level=Objects.requireNonNull(server.getLevel(Level.OVERWORLD));
                    for(int z=0;z<side;z++)for(int x=0;x<side;x++)if(level.getChunkSource().hasChunk(origin+x,origin+z))throw new IllegalStateException("Target already loaded");
                    begin.set(System.nanoTime());
                    for(int z=0;z<side;z++)for(int x=0;x<side;x++){ChunkPos pos=new ChunkPos(origin+x,origin+z);level.getChunkSource().addRegionTicket(ticket,pos,0,pos);}
                    ticketsReady=true;
                }catch(Throwable ex){failure.compareAndSet(null,ex);}});
            }
            if(step==2&&ticketsReady){
                step=3;ServerLevel level=Objects.requireNonNull(server.getLevel(Level.OVERWORLD));futures=new ArrayList<>(total);
                // Vanilla's documented public off-owner path queues each request to the chunk owner.
                // Calling this API on the server owner would managed-block each request serially.
                for(int z=0;z<side;z++)for(int x=0;x<side;x++){
                    var f=level.getChunkSource().getChunkFuture(origin+x,origin+z,ChunkStatus.FULL,true);
                    f.whenComplete((value,error)->{if(error!=null)failure.compareAndSet(null,error);if(completed.incrementAndGet()==total)end.compareAndSet(0,System.nanoTime());});futures.add(f);
                }
            }
            if(step==3&&completed.get()==total){
                step=4;server.execute(()->{try{
                    if(!server.isSameThread())throw new IllegalStateException("Chunk verification owner violation");
                    ServerLevel level=Objects.requireNonNull(server.getLevel(Level.OVERWORLD));
                    for(int i=0;i<total;i++){
                        ChunkAccess chunk=futures.get(i).join().left().orElseThrow(()->new IllegalStateException("Chunk loading failure"));
                        if(!chunk.getPos().equals(new ChunkPos(origin+i%side,origin+i/side))||!chunk.getStatus().isOrAfter(ChunkStatus.FULL)||!chunk.isLightCorrect())throw new IllegalStateException("Incomplete or mismatched chunk");
                        terrain.add(digest(level,chunk));
                    }
                }catch(Throwable ex){failure.compareAndSet(null,ex);}finally{
                    ServerLevel level=Objects.requireNonNull(server.getLevel(Level.OVERWORLD));
                    for(int z=0;z<side;z++)for(int x=0;x<side;x++){ChunkPos pos=new ChunkPos(origin+x,origin+z);level.getChunkSource().removeRegionTicket(ticket,pos,0,pos);}
                    verified=true;
                }});
            }
            if(failure.get()!=null&&(step!=4||verified))finish(mc,false);else if(step==4&&verified)finish(mc,true);
        }catch(Throwable ex){failure.compareAndSet(null,ex);finish(mc,false);}
    }
    private static String digest(ServerLevel level,ChunkAccess chunk)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");ByteBuffer data=ByteBuffer.allocate(16384);
        data.putInt(chunk.getPos().x).putInt(chunk.getPos().z).putInt(chunk.getMinBuildHeight());
        for(LevelChunkSection section:chunk.getSections()){
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                if(data.remaining()<4){hash.update(data.array(),0,data.position());data.clear();}data.putInt(Block.getId(section.getBlockState(x,y,z)));
            }
            hash.update(data.array(),0,data.position());data.clear();
            for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++){
                var key=Objects.requireNonNull(level.registryAccess().registryOrThrow(Registries.BIOME).getKey(section.getBiomes().get(x,y,z).value()));
                hash.update(key.toString().getBytes(StandardCharsets.UTF_8));hash.update((byte)0);
            }
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void finish(Minecraft mc,boolean passed){
        if(stop!=0)return;stop=System.nanoTime();JsonObject r=new JsonObject();r.addProperty("passed",passed);r.addProperty("step",step);r.addProperty("error_type",failure.get()==null?"":failure.get().getClass().getName());
        r.addProperty("scenario","144 originally ungenerated Overworld chunks, x/z 1024..1035, FULL futures via normal off-owner API; tickets and block/biome verification on server owner; no FPS accepted");
        r.addProperty("requested_chunks",total);r.addProperty("completed_futures",completed.get());r.addProperty("verified_full_lit_chunks",terrain.size());r.addProperty("generation_wall_s",end.get()>begin.get()?(end.get()-begin.get())/1e9:-1);
        r.addProperty("terrain_digest_scope","All block-state registry IDs and biome keys in every section of each target chunk; excludes entity/NBT/lighting-array parity");
        JsonArray hashes=new JsonArray();terrain.forEach(hashes::add);r.add("terrain_sha256_ordered",hashes);
        synchronized(ticks){double[] values=ticks.stream().mapToDouble(Double::doubleValue).sorted().toArray();r.addProperty("server_tick_interval_samples",values.length);if(values.length>0){r.addProperty("server_tick_interval_p99_ms",values[Math.min(values.length-1,(int)Math.ceil(values.length*.99)-1)]);r.addProperty("server_tick_interval_max_ms",values[values.length-1]);}}
        r.addProperty("fps_accepted",false);PackControl.recordRenderer(r);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("mvh-chunk-purpose.json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});System.out.println("[MVH Chunk Purpose] passed="+passed+" completed="+completed.get());
    }
}
