package net.vulkanmod.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact original sampler caller with a packaged, hash-gated GAME call-site repair.
 * This covers one original sampler implementation only; no provider-wide acceptance.
 * All other classes, API descriptors, renderer conflicts and native policy retain their gates. */
final class EssentialSamplerCallerAudit {
    private static final Map<String,String> PINS=Map.of("gg/essential/universal/render/UGpuSamplerImpl.class","8225cfaf26b2c9d78c49f2f921e03474fe3c6032375f09f92e50e0b6fddbb3ab");
    private EssentialSamplerCallerAudit(){}
    static boolean translated(String entry,byte[] bytes) {
        String pin=PINS.get(entry);if(pin==null)return false;
        try{
            if(!pin.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            // The packaged transport and automatic production Mixin must both be present.
            try(InputStream config=EssentialSamplerCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!text.contains("\"compatibility.essential.EssentialSamplerCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeEssentialSamplerApi.class","/net/vulkanmod/gl/EssentialSamplerCallers.class","/net/vulkanmod/mixin/compatibility/essential/EssentialSamplerCallerMixin.class"})
                if(EssentialSamplerCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
