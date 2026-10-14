package net.vulkanmod.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact original pixel callers with a packaged, hash-gated GAME call-site repair.
 * This covers these two pixel buffer implementations only; no provider-wide acceptance.
 * All other classes, API descriptors, renderer conflicts and native policy retain their gates. */
final class XaeroPixelCallerAudit {
    private static final Map<String,String> PINS=Map.of(
        "xaero/map/graphics/PixelBuffers.class","0c52e8e472181cbd91eab5d7cbff5e8e8251dcf87de55c5a8697f0d5f46181d2",
        "xaero/common/graphics/PixelBuffers.class","e3fb1593355182d3a55908078f01cfd650f2e4a46936b3be09ef9be2a660be8e");
    private XaeroPixelCallerAudit(){}
    static boolean translated(String entry,byte[] bytes) {
        String pin=PINS.get(entry);if(pin==null)return false;
        try{
            if(!pin.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            // The packaged transport and automatic production Mixin must both be present.
            try(InputStream config=XaeroPixelCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!text.contains("\"compatibility.xaero.XaeroPixelCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativePixelBufferApi.class","/net/vulkanmod/gl/XaeroPixelCallers.class","/net/vulkanmod/mixin/compatibility/essential/EssentialViewportCallerMixin.class"})
                if(XaeroPixelCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
