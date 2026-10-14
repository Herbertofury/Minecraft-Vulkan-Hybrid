package com.axalotl.async.forge.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Fail-closed gating for optional Embeddium renderer hooks. */
public final class HariForgeMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){
        if(mixinClassName.contains(".client.c2me.")) {
            var list = FMLLoader.getLoadingModList();
            return list.getModFileById("c2meforge") != null || list.getModFileById("c2me") != null
                    || list.getModFileById("c2mef") != null || list.getModFileById("c2me_base") != null;
        }
        if(mixinClassName.contains(".client.opengl.")) return !net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled();
        if(mixinClassName.contains(".client.rubidium.")) {
            var list = FMLLoader.getLoadingModList();
            return list.getModFileById("rubidium") != null && list.getModFileById("embeddium") == null;
        }
        if(!mixinClassName.contains(".client.embeddium.")) return true;
        try {
            var list=FMLLoader.getLoadingModList();
            boolean embeddium=list.getModFileById("embeddium")!=null;
            boolean external=list.getModFileById("nvidium")!=null || list.getModFileById("alloyium")!=null;
            boolean shaders=list.getModFileById("oculus")!=null || list.getModFileById("iris")!=null;
            return embeddium && !external && !shaders;
        } catch(Throwable ignored){ return false; }
    }
    @Override public void acceptTargets(Set<String> myTargets,Set<String> otherTargets){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String targetClassName,ClassNode targetClass, String mixinClassName,IMixinInfo mixinInfo){
        if (mixinClassName.endsWith(".RubidiumChunkCacheMixin"))
            com.axalotl.async.forge.client.RubidiumCacheLock.apply(targetClass);
    }
    @Override public void postApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo mixinInfo){
        if (mixinClassName.endsWith(".C2meLightTicketMixin"))
            com.axalotl.async.forge.client.C2meLightTicketLevels.apply(targetClass);
    }
}
