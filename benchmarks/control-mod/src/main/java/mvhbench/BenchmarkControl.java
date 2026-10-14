package mvhbench;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
@Mod("mvhbench")
public final class BenchmarkControl {
 private int ticks; private int completedTicks=-1; private boolean initialShot;
 public BenchmarkControl(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 private void tick(TickEvent.ClientTickEvent event){
  if(event.phase!=TickEvent.Phase.END)return;
  Minecraft mc=Minecraft.getInstance();
  if(mc.level==null||mc.player==null)return;
  Path report=mc.gameDirectory.toPath().resolve("harimt-fps-last.json");
  if(Files.isRegularFile(report)) {try {if(Files.readString(report).contains("\"status\": \"invalid\"")){mc.stop();return;}}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
  if(mc.screen instanceof PauseScreen){mc.setScreen(null);mc.mouseHandler.releaseMouse();}
  if(mc.screen!=null)return;
  if(mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.releaseMouse();
  mc.player.setPos(0.5,110.5,0.5);mc.player.setDeltaMovement(0,0,0);
  mc.player.setYRot(-68.7007f);mc.player.setXRot(9.999512f);
  mc.player.yRotO=-68.7007f;mc.player.xRotO=9.999512f;
  ticks++;
  if(!initialShot&&ticks>=800){ initialShot=true; Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{}); }
  if(completedTicks<0&&Files.isRegularFile(report)){
   try { if(Files.readString(report).contains("\"status\": \"complete\"")){completedTicks=ticks;Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});}}
   catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
  }
  if(completedTicks>=0&&ticks-completedTicks>=60){mc.stop();}
 }
}
