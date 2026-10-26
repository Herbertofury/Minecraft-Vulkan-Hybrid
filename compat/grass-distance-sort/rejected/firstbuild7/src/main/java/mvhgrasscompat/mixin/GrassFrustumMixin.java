package mvhgrasscompat.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import mvhgrasscompat.ExactFrustumCache;
import mvhgrasscompat.FrustumReader;
import mvhgrasscompat.GrassCompat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Original pure visibility result only; original scheduling, builds, queues and settings remain. */
@Pseudo @Mixin(targets="com.leonardoinc22.shortgrass.client.render.GrassSectionCache",remap=false)
public abstract class GrassFrustumMixin {
    @Inject(method="buildBudgeted",at=@At("HEAD"),require=1,allow=1,remap=false)
    private static void mvh$state(ClientLevel level,int x,int y,int z,int radius,int vertical,Vec3 camera,long tick,boolean iris,boolean compute,TextureAtlasSprite blade,TextureAtlasSprite bands,Frustum frustum,CallbackInfo ci){
        mvhgrasscompat.GrassVisibilityReuse.begin(frustum);
    }
    @Redirect(method="buildBudgeted",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/culling/Frustum;m_113029_(Lnet/minecraft/world/phys/AABB;)Z"),require=2,allow=2,remap=false)
    private static boolean mvh$visible(Frustum frustum,AABB box){
        return mvhgrasscompat.GrassVisibilityReuse.visible(frustum,box);
    }
    @Inject(method="buildBudgeted",at=@At("RETURN"),require=1,allow=1,remap=false)
    private static void mvh$end(ClientLevel level,int x,int y,int z,int radius,int vertical,Vec3 camera,long tick,boolean iris,boolean compute,TextureAtlasSprite blade,TextureAtlasSprite bands,Frustum frustum,CallbackInfo ci){mvhgrasscompat.GrassVisibilityReuse.end();}
    @Inject(method="disposeAll",at=@At("HEAD"),require=1,allow=1,remap=false)
    private static void mvh$reload(CallbackInfo ci){mvhgrasscompat.GrassVisibilityReuse.reload();}
}
