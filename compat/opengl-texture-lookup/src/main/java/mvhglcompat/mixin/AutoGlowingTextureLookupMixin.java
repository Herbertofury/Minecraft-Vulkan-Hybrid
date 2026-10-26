package mvhglcompat.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import mvhglcompat.TextureLookup;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="software.bernie.geckolib.cache.texture.AutoGlowingTexture",remap=false)
public abstract class AutoGlowingTextureLookupMixin {
 @Redirect(method="loadTexture",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;m_18691_(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),remap=false,require=1)
 private <T> CompletableFuture<T> mvh$lookupOnTextureOwner(Minecraft client,Supplier<T> lookup){
  return TextureLookup.submit(RenderSystem.isOnRenderThread(),lookup,client::submit);
 }
}
