package net.vulkanmod.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Exact original shader callers with a packaged, hash-gated GAME call-site repair.
 * This covers these five shader implementations only; no provider-wide acceptance.
 * All other classes, API descriptors, renderer conflicts and native policy retain their gates. */
final class EssentialShaderCallerAudit {
    private static final String PREFIX="gg/essential/elementa/shaders/";
    private static final Map<String,String> PINS=Map.of(
        PREFIX+"FloatUniform.class","50acc9988f6c6e886d3a2ae5ed7768042826e9b7236eea8660f50f18419471b5",
        PREFIX+"IntUniform.class","23a3cc8f07c538dd525bd7656ccb55c82597c5479acf56ea328c1dd1324e72fa",
        PREFIX+"Shader.class","015b7d0967892e0a26809b91954375c00639dcc4725d9557424540278af6701e",
        PREFIX+"Vec2Uniform.class","f94816f3128bc0ff028e29dacd660fb65d399b32ae82ac51809d2d89483336da",
        PREFIX+"Vec4Uniform.class","313d30f932d18a205e753ad48b4a5e4eddff37296c8f63dda8f5cd0b002510fb");
    private EssentialShaderCallerAudit(){}
    static boolean translated(String entry,byte[] bytes) {
        String pin=PINS.get(entry);if(pin==null)return false;
        try{
            if(!pin.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))return false;
            // The packaged transport and automatic production Mixin must both be present.
            try(InputStream config=EssentialShaderCallerAudit.class.getResourceAsStream("/vulkanmod.mixins.json")){
                if(config==null)return false;String text=new String(config.readAllBytes(),StandardCharsets.UTF_8);
                if(!text.contains("\"plugin\": \"net.vulkanmod.mixin.MixinPlugin\"")||!text.contains("\"compatibility.essential.EssentialShaderCallerMixin\""))return false;
            }
            for(String resource:new String[]{"/net/vulkanmod/gl/NativeShaderApi.class","/net/vulkanmod/gl/EssentialShaderCallers.class","/net/vulkanmod/mixin/compatibility/essential/EssentialShaderCallerMixin.class"})
                if(EssentialShaderCallerAudit.class.getResource(resource)==null)return false;
            return true;
        }catch(Exception failure){return false;}
    }
}
