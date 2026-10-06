package mvhgrasscompat;
import java.lang.invoke.*;
import net.minecraft.world.phys.AABB;
/** Calls the pinned original package-private bounds method; no recreated grass geometry. */
public final class GrassOriginalBounds {
    private static final MethodHandle bounds=resolve();
    private static MethodHandle resolve(){try{
        Class<?> type=Class.forName("com.leonardoinc22.shortgrass.client.render.GrassDrawDispatcher");
        return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).findStatic(type,"sectionBounds",MethodType.methodType(AABB.class,int.class,int.class,int.class));
    }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
    public static AABB sectionBounds(int x,int y,int z){try{return (AABB)bounds.invokeExact(x,y,z);}catch(Throwable failure){throw new IllegalStateException("Original grass bounds invocation failed",failure);}}
}
