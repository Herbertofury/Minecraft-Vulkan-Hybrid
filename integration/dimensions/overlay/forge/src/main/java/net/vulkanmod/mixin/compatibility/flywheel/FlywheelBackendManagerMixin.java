package net.vulkanmod.mixin.compatibility.flywheel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Method;

/** Preserve the original engine decision for the registered native provider; legacy GL engines remain blocked. */
@Pseudo
@Mixin(targets="dev.engine_room.flywheel.impl.BackendManagerImpl",remap=false)
public class FlywheelBackendManagerMixin {
    private static Method mvh$nativeSelection;
    private static boolean mvh$checkedProvider;
    @Inject(method="isBackendOn",at=@At("HEAD"),cancellable=true,remap=false)
    private static void vulkanmod$selectFlywheelBackend(CallbackInfoReturnable<Boolean> cir){
        if(!mvh$checkedProvider){
            mvh$checkedProvider=true;
            try{mvh$nativeSelection=Class.forName("mvhflywheelbackend.NativeEngine").getMethod("selectedForCurrentSession");}
            catch(ClassNotFoundException|NoSuchMethodException absent){mvh$nativeSelection=null;}
        }
        if(mvh$nativeSelection!=null){
            try{if(Boolean.TRUE.equals(mvh$nativeSelection.invoke(null)))return;}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Native Flywheel provider selection failed",failure);}
        }
        cir.setReturnValue(false);
    }
}
