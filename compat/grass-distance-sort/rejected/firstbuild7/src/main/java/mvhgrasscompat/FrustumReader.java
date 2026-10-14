package mvhgrasscompat;
import java.lang.reflect.Field;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
/** Read the installed 1.20.1 official/SRG pure-frustum state; unavailable state disables reuse. */
public final class FrustumReader {
    private static Field matrix,x,y,z;
    private static boolean unavailable;
    public static boolean capture(Frustum frustum,float[] values,double[] camera){
        if(unavailable)return false;
        try{
            if(matrix==null){matrix=field("f_252406_","matrix",Matrix4f.class);x=field("f_112996_","camX",double.class);y=field("f_112997_","camY",double.class);z=field("f_112998_","camZ",double.class);}
            ((Matrix4f)matrix.get(frustum)).get(values);camera[0]=x.getDouble(frustum);camera[1]=y.getDouble(frustum);camera[2]=z.getDouble(frustum);return true;
        }catch(ReflectiveOperationException|RuntimeException failure){unavailable=true;return false;}
    }
    private static Field field(String runtime,String mapped,Class<?> type)throws ReflectiveOperationException{
        Field f;try{f=Frustum.class.getDeclaredField(runtime);}catch(NoSuchFieldException missing){f=Frustum.class.getDeclaredField(mapped);}
        if(f.getType()!=type)throw new NoSuchFieldException("Unexpected frustum state type");f.setAccessible(true);return f;
    }
}
