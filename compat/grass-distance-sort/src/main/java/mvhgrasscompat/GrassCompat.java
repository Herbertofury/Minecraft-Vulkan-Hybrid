package mvhgrasscompat;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.file.Files;
/** Only the exact private performance build whose pure comparator was inspected is supported. */
@Mod("mvhgrasscompat")
public final class GrassCompat {
 public GrassCompat() throws Exception {
  var path=ModList.get().getModFileById("grassiergrass").getFile().getFilePath();
  String expected="91006e8220e65572d7e9e1e54b6058526c4adf908faedc750c9f19f13623df8c";
  String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
  if(!expected.equals(actual))throw new IllegalStateException("MVH grass compatibility requires the inspected performance replacement JAR");
 }
}
