package mvhgrasscompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
/** Render-owner pure visibility state shared only within the original buildBudgeted call. */
public final class GrassVisibilityReuse {
    private static final ExactFrustumCache cache=new ExactFrustumCache();
    private static final float[] matrix=new float[16];private static final double[] camera=new double[3];private static Frustum active;
    public static void begin(Frustum frustum){
        if(!RenderSystem.isOnRenderThread())return;active=null;
        if(!GrassCompat.FRUSTUM_REUSE_ALLOWED||frustum==null||frustum.getClass()!=Frustum.class||!FrustumReader.capture(frustum,matrix,camera))return;
        cache.state(matrix,camera[0],camera[1],camera[2]);active=frustum;
    }
    public static boolean activeFor(Frustum frustum){return RenderSystem.isOnRenderThread()&&active!=null&&frustum==active;}
    public static boolean visible(Frustum frustum,AABB box){
        if(!activeFor(frustum))return frustum.isVisible(box);
        byte old=cache.lookup(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ);if(old!=0)return old==2;
        boolean result=frustum.isVisible(box);cache.put(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,result);return result;
    }
    public static void end(){if(RenderSystem.isOnRenderThread())active=null;}
    public static void reload(){if(RenderSystem.isOnRenderThread()){active=null;cache.clear();}}
}
