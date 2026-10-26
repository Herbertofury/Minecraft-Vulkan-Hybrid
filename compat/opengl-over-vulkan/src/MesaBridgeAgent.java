import java.lang.instrument.Instrumentation;
import java.nio.file.*;
/** Preloads only the source-built, profile-scoped Mesa OpenGL implementation. */
public final class MesaBridgeAgent {
 public static void premain(String unused,Instrumentation instrumentation)throws Exception {
  String raw=System.getProperty("mvh.zink.library");if(raw==null)throw new IllegalArgumentException("Explicit source-built Mesa library required");
  Path file=Path.of(raw).toAbsolutePath().normalize();if(!file.getFileName().toString().equalsIgnoreCase("opengl32.dll")||!Files.isRegularFile(file))throw new IllegalArgumentException("Expected explicit Mesa opengl32.dll");
  if(!"zink".equals(System.getenv("GALLIUM_DRIVER")))throw new IllegalArgumentException("Refusing implicit/native/software backend selection; GALLIUM_DRIVER must be zink");
  System.load(file.toString());System.out.println("[MVH Zink] Explicit Mesa library preloaded. A live Zink/NVIDIA renderer check is still required to prove Vulkan use.");
 }
}
