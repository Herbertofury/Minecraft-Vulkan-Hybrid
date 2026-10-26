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
    @Unique private static final ExactFrustumCache mvh$visibility=new ExactFrustumCache();
    @Unique private static final float[] mvh$matrix=new float[16];
    @Unique private static final double[] mvh$camera=new double[3];
    @Unique private static Frustum mvh$activeFrustum;
    @Inject(method="buildBudgeted",at=@At("HEAD"),require=1,allow=1,remap=false)
    private static void mvh$state(ClientLevel level,int x,int y,int z,int radius,int vertical,Vec3 camera,long tick,boolean iris,boolean compute,TextureAtlasSprite blade,TextureAtlasSprite bands,Frustum frustum,CallbackInfo ci){
        if(!RenderSystem.isOnRenderThread())return;
        mvh$activeFrustum=null;
        if(!GrassCompat.FRUSTUM_REUSE_ALLOWED||frustum==null||frustum.getClass()!=Frustum.class)return;
        if(!FrustumReader.capture(frustum,mvh$matrix,mvh$camera))return;
        mvh$visibility.state(mvh$matrix,mvh$camera[0],mvh$camera[1],mvh$camera[2]);mvh$activeFrustum=frustum;
    }
    @Redirect(method="buildBudgeted",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/culling/Frustum;m_113029_(Lnet/minecraft/world/phys/AABB;)Z"),require=2,allow=2,remap=false)
    private static boolean mvh$visible(Frustum frustum,AABB box){
        if(!RenderSystem.isOnRenderThread()||frustum!=mvh$activeFrustum)return frustum.isVisible(box);
        byte old=mvh$visibility.lookup(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);if(old!=0)return old==2;
        boolean result=frustum.isVisible(box);mvh$visibility.put(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,result);return result;
    }
    @Inject(method="buildBudgeted",at=@At("RETURN"),require=1,allow=1,remap=false)
    private static void mvh$end(ClientLevel level,int x,int y,int z,int radius,int vertical,Vec3 camera,long tick,boolean iris,boolean compute,TextureAtlasSprite blade,TextureAtlasSprite bands,Frustum frustum,CallbackInfo ci){if(RenderSystem.isOnRenderThread())mvh$activeFrustum=null;}
    @Inject(method="disposeAll",at=@At("HEAD"),require=1,allow=1,remap=false)
    private static void mvh$reload(CallbackInfo ci){if(RenderSystem.isOnRenderThread()){mvh$activeFrustum=null;mvh$visibility.clear();}}
}
