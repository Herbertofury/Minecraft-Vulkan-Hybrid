package mvhpackbench;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.lang.management.*;
@Mod("mvhpackbench")
public final class PackControl {
 private long joined,started,last,completed; private boolean initialShot,done; private double[] frames=new double[131072];private int count;private long captureEpoch;
 public PackControl(){MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,TickEvent.ClientTickEvent.class,this::tick);MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,TickEvent.RenderTickEvent.class,this::render);System.out.println("[MVH Pack] recorder initialized with explicit event types");}
 private void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
  if(mc.screen instanceof PauseScreen){mc.setScreen(null);mc.mouseHandler.releaseMouse();}
  if(mc.screen!=null)return;SmokeChecks.tick(mc);if(joined==0){joined=System.nanoTime();System.out.println("[MVH Pack] stable scene warmup started");}if(mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.releaseMouse();
  mc.player.setPos(0.5,110.5,0.5);mc.player.setDeltaMovement(0,0,0);mc.player.setYRot(-68.7007f);mc.player.setXRot(9.999512f);mc.player.yRotO=-68.7007f;mc.player.xRotO=9.999512f;
  long now=System.nanoTime();if(!initialShot&&now-joined>=40_000_000_000L){initialShot=true;Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});}
  if(done&&now-completed>=3_000_000_000L)mc.stop();
 }
 private void render(TickEvent.RenderTickEvent e){
  if(e.phase!=TickEvent.Phase.END||done)return;Minecraft mc=Minecraft.getInstance();long now=System.nanoTime();
  if(joined==0||mc.level==null||mc.player==null)return;
  if(mc.screen!=null){if(started!=0)finish("invalid","screen opened during capture");return;}
  if(now-joined<60_000_000_000L)return;
  if(started==0){started=last=now;captureEpoch=System.currentTimeMillis();return;}
  if(count==frames.length)frames=Arrays.copyOf(frames,frames.length*2);frames[count++]=(now-last)/1e6;last=now;
  if(now-started>=30_000_000_000L)finish("complete","");
 }
 private void finish(String status,String reason){
  Minecraft mc=Minecraft.getInstance();done=true;completed=System.nanoTime();JsonObject o=new JsonObject();o.addProperty("status",status);o.addProperty("reason",reason);o.addProperty("capture_epoch_ms",captureEpoch);o.addProperty("duration_ms",(last-started)/1e6);o.addProperty("frames",count);
  double[] sorted=Arrays.copyOf(frames,count);Arrays.sort(sorted);double total=0,slow=0;for(double x:sorted)total+=x;int lows=Math.max(1,(int)Math.ceil(count*.01));for(int i=Math.max(0,count-lows);i<count;i++)slow+=sorted[i];
  if(count>0){o.addProperty("average_fps",count*1000./total);o.addProperty("one_percent_low_fps",lows*1000./slow);o.addProperty("p50_ms",pct(sorted,.50));o.addProperty("p95_ms",pct(sorted,.95));o.addProperty("p99_ms",pct(sorted,.99));o.addProperty("max_ms",sorted[count-1]);}
  JsonArray a=new JsonArray();for(int i=0;i<count;i++)a.add(frames[i]);o.add("all_frame_ms",a);o.addProperty("gl_vendor",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));o.addProperty("gl_renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));o.addProperty("gl_version",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));o.addProperty("recorder","Forge RenderTick END wall intervals; all frames retained; not GPU/presentation timing");o.addProperty("camera","0.5,110.5,0.5 / yaw -68.7007 / pitch 9.999512");
  try{Files.writeString(mc.gameDirectory.toPath().resolve("mvh-pack-fps.json"),new GsonBuilder().setPrettyPrinting().create().toJson(o));}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
  Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});
 }
 private double pct(double[] a,double q){return a[Math.min(a.length-1,(int)Math.ceil(q*a.length)-1)];}
}
