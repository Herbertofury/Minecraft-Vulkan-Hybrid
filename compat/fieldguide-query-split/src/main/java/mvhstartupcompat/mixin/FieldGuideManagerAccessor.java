package mvhstartupcompat.mixin;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
@Pseudo @Mixin(targets="com.evandev.fieldguide.client.ClientFieldGuideManager",remap=false)
public interface FieldGuideManagerAccessor {
    @Invoker(value="getBiomeAdditions",remap=false) List<String> mvh$originalAdditions();
    @Invoker(value="getBiomeRemovals",remap=false) List<String> mvh$originalRemovals();
}
