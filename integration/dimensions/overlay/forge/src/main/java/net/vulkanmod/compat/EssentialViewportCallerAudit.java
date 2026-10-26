package net.vulkanmod.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact original viewport callers with a packaged, hash-gated GAME call-site repair.
 * This covers these four viewport implementations only; no provider-wide acceptance.
 * All other classes, API descriptors, renderer conflicts and native policy retain their gates. */
final class EssentialViewportCallerAudit {
    private static final Map<String,String> PINS=Map.of(
        "gg/essential/universal/render/ViewportState.class","69948326326a1a4078a6c88a8b8f5157b9f6c2fae8b9b70c24c57d4846dcfcb7",
        "gg/essential/universal/render/ViewportState$Companion.class","3a76b292cc424c053a36e4512698bad25cdf5c907a3102b8c6205a7c512ab403",
        "gg/essential/util/GlViewportState.class","aeca3802cede0a10133029ba9fdcb01ff8c958b5807671e828d35d25590e4d5a",
        "gg/essential/util/GlViewportState$Companion.class","5f151d343e96f44645b11c07b68e359643c2c1aeb2efe0f605196470cd1d9e62");
    private EssentialViewportCallerAudit(){}
    static boolean translated(String entry,byte[] bytes) {
        String pin=PINS.get(entry);if(pin==null)return false;
        try{
            if(!pin.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            // The packaged transport and automatic production Mixin must both be present.
            try(InputStream config=EssentialViewportCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!text.contains("\"compatibility.essential.EssentialViewportCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeViewportApi.class","/net/vulkanmod/gl/EssentialViewportCallers.class","/net/vulkanmod/mixin/compatibility/essential/EssentialViewportCallerMixin.class"})
                if(EssentialViewportCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
