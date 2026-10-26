package mvhgrasscompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import mvhgrasscompat.InvalidationRetention;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Filter only while the original owner drains its concurrent producer queues. */
@Pseudo @Mixin(targets="com.leonardoinc22.shortgrass.client.render.GrassSectionCache",remap=false)
public abstract class GrassInvalidationMixin {
    @Shadow @Final private static Long2ObjectOpenHashMap<?> CACHE;
    @Shadow @Final private static LongOpenHashSet IN_FLIGHT;
    @Shadow @Final private static LongOpenHashSet DIRTY_SECTIONS;
    @Shadow @Final private static LongOpenHashSet LIGHT_DIRTY_SECTIONS;

    @Redirect(method="drainPendingDirtySections",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;add(Ljava/lang/Long;)Z"),require=2,allow=2,remap=false)
    private static boolean mvh$retainExistingSnapshotInvalidation(LongOpenHashSet destination,Long section) {
        // Never inspect these owner-confined containers from an unexpected caller.
        if (!RenderSystem.isOnRenderThread() || section==null
                || (destination!=DIRTY_SECTIONS && destination!=LIGHT_DIRTY_SECTIONS)) {
            return destination.add(section);
        }
        long key=section.longValue();
        return InvalidationRetention.retain(CACHE.containsKey(key),IN_FLIGHT.contains(key))
                && destination.add(section);
    }
}
