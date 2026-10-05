package mvhgrasscompat.mixin;
import mvhgrasscompat.DistanceSort;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo @Mixin(targets="com.leonardoinc22.shortgrass.client.render.GrassSectionCache",remap=false)
public abstract class GrassDistanceMixin {
 @Unique private static final ThreadLocal<DistanceSort> mvh$sort=ThreadLocal.withInitial(DistanceSort::new);
 @Shadow private static double sectionDistanceSqr(long section,Vec3 camera){throw new AssertionError();}
 @Redirect(method="buildBudgeted",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/longs/LongArrayList;sort(Lit/unimi/dsi/fastutil/longs/LongComparator;)V"),require=2,allow=2,remap=false)
 private static void mvh$computeDistanceOnce(LongArrayList entries,LongComparator original,@Local(argsOnly=true) Vec3 camera) {
  if(entries.size()<2){entries.sort(original);return;}
  mvh$sort.get().sort(entries.elements(),entries.size(),value->sectionDistanceSqr(value,camera));
 }
}
