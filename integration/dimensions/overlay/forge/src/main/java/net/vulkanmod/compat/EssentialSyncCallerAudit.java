package net.vulkanmod.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact original fence callers with a packaged, hash-gated GAME call-site repair.
 * This covers these two fence implementations only; no provider-wide acceptance.
 * All other classes, API descriptors, renderer conflicts and native policy retain their gates. */
final class EssentialSyncCallerAudit {
    private static final Map<String,String> PINS=Map.of(
        "gg/essential/universal/render/UGpuFenceImplKt.class","b413061981085eff3d3a12451d54abec66cdb0d4e11a55f7950233147d73b4c9",
        "gg/essential/universal/render/UGpuFenceImpl.class","926eb49b73c7841210b683d9136b9fede90c6500a94662d3a73a30f56e7da291");
    private EssentialSyncCallerAudit(){}
    static boolean translated(String entry,byte[] bytes) {
        String pin=PINS.get(entry);if(pin==null)return false;
        try{
            if(!pin.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            // The packaged transport and automatic production Mixin must both be present.
            try(InputStream config=EssentialSyncCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!text.contains("\"compatibility.essential.EssentialSyncCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeSyncApi.class","/net/vulkanmod/gl/EssentialSyncCallers.class","/net/vulkanmod/mixin/compatibility/essential/EssentialSyncCallerMixin.class"})
                if(EssentialSyncCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
