package net.vulkanmod.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact original clipping callers with a packaged, hash-gated GAME call-site repair.
 * This covers these five clipping implementations only; no provider-wide acceptance.
 * All other classes, API descriptors, renderer conflicts and native policy retain their gates. */
final class EssentialClipCallerAudit {
    private static final Map<String,String> PINS=Map.of(
        "gg/essential/universal/render/ScissorState.class","56f542d38fc64506bbbe361d9b39f038c24a53494f4b1cb19bd1253c61740524",
        "gg/essential/universal/render/ScissorState$Companion.class","4c31aa2d19bf7d94189ccec519e9c2ed9c7e4ed89a35e438ffe2b1108b45d9c6",
        "gg/essential/util/ScissorState.class","bcdfd23b1b3acfa15890d9860e3e206a726415bb26699fd5b2b5da879a7d4070",
        "gg/essential/util/ScissorState$Companion.class","60766fc773cc78964374e033520084991b2daa0890b4ed0fd87fe9ce0e578884",
        "gg/essential/elementa/effects/StencilEffect.class","dc2b713c8b22eb6443b21c6661d70d151c205139bde88aba3bc1fac41fbd6e7d");
    private EssentialClipCallerAudit(){}
    static boolean translated(String entry,byte[] bytes) {
        String pin=PINS.get(entry);if(pin==null)return false;
        try{
            if(!pin.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            // The packaged transport and automatic production Mixin must both be present.
            try(InputStream config=EssentialClipCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!text.contains("\"compatibility.essential.EssentialClipCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeClipApi.class","/net/vulkanmod/gl/EssentialClipCallers.class","/net/vulkanmod/mixin/compatibility/essential/EssentialClipCallerMixin.class"})
                if(EssentialClipCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
