package mvhgrasscompat.mixin;
import it.unimi.dsi.fastutil.longs.*;
import mvhgrasscompat.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.sugar.Local;
/** Move only pure first-build visibility ahead of redundant eligibility lookups. */
@Pseudo @Mixin(targets="com.leonardoinc22.shortgrass.client.render.GrassSectionCache",remap=false)
public abstract class GrassFirstBuildMixin {
    @Shadow @Final private static Long2ObjectOpenHashMap<?> CACHE;
    @Shadow @Final private static LongOpenHashSet IN_FLIGHT;
    @Redirect(method="buildBudgeted",slice=@Slice(from=@At(value="NEW",target="net/minecraft/client/renderer/chunk/RenderRegionCache",remap=false)),at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;contains(J)Z",ordinal=0),require=1,allow=1,remap=false)
    private static boolean mvh$firstBuildVisibility(LongOpenHashSet set,long key,@Local(argsOnly=true) Frustum frustum){
        boolean inFlight=set.contains(key);if(inFlight||!GrassVisibilityReuse.activeFor(frustum))return inFlight;
        if(set!=IN_FLIGHT)throw new IllegalStateException("Unexpected grass first-build set");
        boolean cached=CACHE.containsKey(key);if(cached)return false;
        var box=GrassOriginalBounds.sectionBounds(SectionPos.sectionToBlockCoord(SectionPos.x(key)),SectionPos.sectionToBlockCoord(SectionPos.y(key)),SectionPos.sectionToBlockCoord(SectionPos.z(key)));
        boolean skipped=FirstBuildEligibility.skip(false,false,true,GrassVisibilityReuse.visible(frustum,box));if(skipped)FirstBuildEligibility.SKIPPED_CANDIDATES++;return skipped;
    }
}
