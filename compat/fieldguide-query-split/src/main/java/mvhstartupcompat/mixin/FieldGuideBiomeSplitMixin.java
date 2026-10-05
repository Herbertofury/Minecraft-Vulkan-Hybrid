package mvhstartupcompat.mixin;
import mvhstartupcompat.BiomeQuerySplitCache;
import mvhstartupcompat.BiomeModifierIndex;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo @Mixin(targets="com.evandev.fieldguide.client.search.SearchManager",remap=false)
public abstract class FieldGuideBiomeSplitMixin {
 @Unique private static final ThreadLocal<BiomeQuerySplitCache> mvh$pipeSplits=ThreadLocal.withInitial(()->new BiomeQuerySplitCache(4096));
 @Unique private static final java.util.concurrent.atomic.AtomicBoolean mvh$reportedReuse=new java.util.concurrent.atomic.AtomicBoolean();
 @Redirect(method="matchByBiome",at=@At(value="INVOKE",target="Ljava/lang/String;split(Ljava/lang/String;)[Ljava/lang/String;"),require=2,allow=2,remap=false)
 private static String[] mvh$reuseReadOnlyPipeSplit(String input,String regex){
  BiomeQuerySplitCache cache=mvh$pipeSplits.get();String[] result=cache.split(input,regex);
  if(cache.hits()>=256&&mvh$reportedReuse.compareAndSet(false,true))System.out.println("[MVH Startup Compat] Field Guide biome split reuse verified: 256 hits; content-keyed cache bounded at 4096 entries per owner.");
  return result;
 }
 @Unique private static final ThreadLocal<BiomeModifierIndex> mvh$queryIndex=new ThreadLocal<>();
 @Inject(method="matchByBiome",at=@At("HEAD"),require=1,remap=false)
 private static void mvh$beginQuery(String query,List<Object> entries,boolean exact,CallbackInfoReturnable<List<Object>> ci){mvh$queryIndex.set(new BiomeModifierIndex(mvh$pipeSplits.get()));}
 @Inject(method="matchByBiome",at=@At("RETURN"),require=1,remap=false)
 private static void mvh$endQuery(String query,List<Object> entries,boolean exact,CallbackInfoReturnable<List<Object>> ci){mvh$queryIndex.remove();}
 @Unique private static BiomeModifierIndex mvh$index(){BiomeModifierIndex index=mvh$queryIndex.get();if(index==null){index=new BiomeModifierIndex(mvh$pipeSplits.get());mvh$queryIndex.set(index);}return index;}
 @Redirect(method="matchByBiome",at=@At(value="INVOKE",target="Lcom/evandev/fieldguide/client/ClientFieldGuideManager;getBiomeAdditions()Ljava/util/List;"),require=1,allow=1,remap=false)
 private static List<String> mvh$entryAdditions(@Coerce Object manager,@Local(ordinal=0) ResourceLocation entryId){return mvh$index().matching(((FieldGuideManagerAccessor)manager).mvh$originalAdditions(),entryId.toString());}
 @Redirect(method="matchByBiome",at=@At(value="INVOKE",target="Lcom/evandev/fieldguide/client/ClientFieldGuideManager;getBiomeRemovals()Ljava/util/List;"),require=1,allow=1,remap=false)
 private static List<String> mvh$entryRemovals(@Coerce Object manager,@Local(ordinal=0) ResourceLocation entryId){return mvh$index().matching(((FieldGuideManagerAccessor)manager).mvh$originalRemovals(),entryId.toString());}

}
