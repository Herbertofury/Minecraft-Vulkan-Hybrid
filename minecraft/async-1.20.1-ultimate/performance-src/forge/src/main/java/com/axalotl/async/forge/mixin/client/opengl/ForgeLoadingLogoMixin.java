package com.axalotl.async.forge.mixin.client.opengl;

import com.axalotl.async.forge.client.OwnedLoadingLogo;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeLoadingOverlay.class, remap = false)
public abstract class ForgeLoadingLogoMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private OwnedLoadingLogo harimt$logo;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/earlydisplay/DisplayWindow;addMojangTexture(I)V"), remap = false)
    private void harimt$ownLogo(DisplayWindow window, int sharedTextureId) {
        // OptiFine's resource reload releases the TextureManager logo ID that
        // Forge captured. This private texture is never registered with that
        // manager, so its captured ID remains valid for the whole overlay.
        harimt$logo = new OwnedLoadingLogo(minecraft);
        window.addMojangTexture(harimt$logo.getId());
    }

    @Inject(method = {"render", "m_88315_"}, at = @At("TAIL"), remap = false)
    private void harimt$releaseLogo(CallbackInfo ci) {
        if (harimt$logo != null && minecraft.getOverlay() != (Object) this) {
            harimt$logo.releaseId();
            harimt$logo.close();
            harimt$logo = null;
        }
    }
}
