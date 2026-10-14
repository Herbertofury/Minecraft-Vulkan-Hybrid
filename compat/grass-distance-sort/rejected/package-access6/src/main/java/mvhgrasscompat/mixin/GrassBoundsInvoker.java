package mvhgrasscompat.mixin;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
@Pseudo @Mixin(targets="com.leonardoinc22.shortgrass.client.render.GrassDrawDispatcher",remap=false)
public interface GrassBoundsInvoker {
    @Invoker(value="sectionBounds",remap=false) static AABB mvh$sectionBounds(int x,int y,int z){throw new AssertionError("Original grass bounds invoker unavailable");}
}
