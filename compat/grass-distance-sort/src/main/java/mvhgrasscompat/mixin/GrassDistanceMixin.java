package mvhgrasscompat.mixin;
import mvhgrasscompat.MemoizedDistanceSort;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo @Mixin(targets="com.leonardoinc22.shortgrass.client.render.GrassSectionCache",remap=false)
public abstract class GrassDistanceMixin {
 @Unique private static final ThreadLocal<MemoizedDistanceSort> mvh$buildSort=ThreadLocal.withInitial(MemoizedDistanceSort::new);
 @Unique private static final ThreadLocal<MemoizedDistanceSort> mvh$refreshSort=ThreadLocal.withInitial(MemoizedDistanceSort::new);
 @Shadow private static double sectionDistanceSqr(long section,Vec3 camera){throw new AssertionError();}
 @Redirect(method="buildBudgeted",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/longs/LongArrayList;sort(Lit/unimi/dsi/fastutil/longs/LongComparator;)V",ordinal=0),require=1,allow=1,remap=false)
 private static void mvh$sortBuild(LongArrayList entries,LongComparator original,@Local(argsOnly=true) Vec3 camera) {
  if(entries.size()<2){entries.sort(original);return;}
  mvh$buildSort.get().sort(entries.elements(),entries.size(),camera.x,camera.y,camera.z,value->sectionDistanceSqr(value,camera));
 }
 @Redirect(method="buildBudgeted",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/longs/LongArrayList;sort(Lit/unimi/dsi/fastutil/longs/LongComparator;)V",ordinal=1),require=1,allow=1,remap=false)
 private static void mvh$sortRefresh(LongArrayList entries,LongComparator original,@Local(argsOnly=true) Vec3 camera) {
  if(entries.size()<2){entries.sort(original);return;}
  mvh$refreshSort.get().sort(entries.elements(),entries.size(),camera.x,camera.y,camera.z,value->sectionDistanceSqr(value,camera));
 }
}
